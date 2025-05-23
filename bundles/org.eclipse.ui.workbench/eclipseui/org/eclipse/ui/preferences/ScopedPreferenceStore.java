/*******************************************************************************
 * Copyright (c) 2004, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Yves YANG <yves.yang@soyatec.com> -
 *     		Initial Fix for Bug 138078 [Preferences] Preferences Store for i18n support
 *******************************************************************************/
package org.eclipse.ui.preferences;

import java.io.IOException;
import java.util.Objects;
import org.eclipse.core.commands.common.EventManager;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.INodeChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.NodeChangeEvent;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.osgi.service.prefs.BackingStoreException;

/**
 * The ScopedPreferenceStore is an IPreferenceStore that uses the scopes
 * provided in org.eclipse.core.runtime.preferences.
 * <p>
 * A ScopedPreferenceStore does the lookup of a preference based on it's search
 * scopes and sets the value of the preference based on its store scope.
 * </p>
 * <p>
 * The default scope is always included in the search scopes when searching for
 * preference values.
 * </p>
 *
 * @see org.eclipse.core.runtime.preferences
 * @since 3.1
 */
public class ScopedPreferenceStore extends EventManager implements IPreferenceStore, IPersistentPreferenceStore {

	/**
	 * The storeContext is the context where values will stored with the setValue
	 * methods. If there are no searchContexts this will be the search context.
	 * (along with the "default" context)
	 */
	private IScopeContext storeContext;

	/**
	 * The searchContext is the array of contexts that will be used by the get
	 * methods for searching for values.
	 */
	private IScopeContext[] searchContexts;

	/**
	 * A boolean to indicate the property changes should not be propagated.
	 */
	protected boolean silentRunning = false;

	/**
	 * The listener on the IEclipsePreferences. This is used to forward updates to
	 * the property change listeners on the preference store.
	 */
	private EclipsePreferencesListener preferencesListener;

	/**
	 * The default context is the context where getDefault and setDefault methods
	 * will search. This context is also used in the search.
	 */
	private IScopeContext defaultContext = DefaultScope.INSTANCE;

	/**
	 * The nodeQualifer is the string used to look up the node in the contexts.
	 */
	String nodeQualifier;

	/**
	 * The defaultQualifier is the string used to look up the default node.
	 */
	String defaultQualifier;

	/**
	 * Boolean value indicating whether or not this store has changes to be saved.
	 */
	private boolean dirty;

	/**
	 * Create a new instance of the receiver. Store the values in context in the
	 * node looked up by qualifier. <strong>NOTE:</strong> Any instance of
	 * ScopedPreferenceStore should call
	 *
	 * @param context              the scope to store to
	 * @param qualifier            the qualifier used to look up the preference node
	 * @param defaultQualifierPath the qualifier used when looking up the defaults
	 */
	public ScopedPreferenceStore(IScopeContext context, String qualifier, String defaultQualifierPath) {
		this(context, qualifier);
		this.defaultQualifier = defaultQualifierPath;
	}

	/**
	 * Create a new instance of the receiver. Store the values in context in the
	 * node looked up by qualifier.
	 *
	 * @param context   the scope to store to
	 * @param qualifier the qualifer used to look up the preference node
	 */
	public ScopedPreferenceStore(IScopeContext context, String qualifier) {
		storeContext = context;
		this.nodeQualifier = qualifier;
		this.defaultQualifier = qualifier;
	}

	/**
	 * Initialize the preferences listener.
	 */
	private void initializePreferencesListener() {
		if (preferencesListener == null) {
			preferencesListener = new EclipsePreferencesListener(this);
		}

	}

	/**
	 * Does its best at determining the default value for the given key. Checks the
	 * given object's type and then looks in the list of defaults to see if a value
	 * exists. If not or if there is a problem converting the value, the default
	 * default value for that type is returned.
	 *
	 * @param key the key to search
	 * @param obj the object who default we are looking for
	 * @return Object or <code>null</code>
	 */
	Object getDefault(String key, Object obj) {
		IEclipsePreferences defaults = getDefaultPreferences();
		if (obj instanceof String) {
			return defaults.get(key, STRING_DEFAULT_DEFAULT);
		} else if (obj instanceof Integer) {
			return Integer.valueOf(defaults.getInt(key, INT_DEFAULT_DEFAULT));
		} else if (obj instanceof Double) {
			return Double.valueOf(defaults.getDouble(key, DOUBLE_DEFAULT_DEFAULT));
		} else if (obj instanceof Float) {
			return Float.valueOf(defaults.getFloat(key, FLOAT_DEFAULT_DEFAULT));
		} else if (obj instanceof Long) {
			return Long.valueOf(defaults.getLong(key, LONG_DEFAULT_DEFAULT));
		} else if (obj instanceof Boolean) {
			return defaults.getBoolean(key, BOOLEAN_DEFAULT_DEFAULT) ? Boolean.TRUE : Boolean.FALSE;
		} else {
			return null;
		}
	}

	/**
	 * Return the IEclipsePreferences node associated with this store.
	 *
	 * @return the preference node for this store
	 */
	IEclipsePreferences getStorePreferences() {
		return storeContext.getNode(nodeQualifier);
	}

	/**
	 * Return the default IEclipsePreferences for this store.
	 *
	 * @return this store's default preference node
	 */
	private IEclipsePreferences getDefaultPreferences() {
		return defaultContext.getNode(defaultQualifier);
	}

	@Override
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		initializePreferencesListener();// Create the preferences listener if it
		// does not exist
		addListenerObject(listener);
	}

	/**
	 * Return the preference path to search preferences on. This is the list of
	 * preference nodes based on the scope contexts for this store. If there are no
	 * search contexts set, then return this store's context.
	 * <p>
	 * Whether or not the default context should be included in the resulting list
	 * is specified by the <code>includeDefault</code> parameter.
	 * </p>
	 *
	 * @param includeDefault <code>true</code> if the default context should be
	 *                       included and <code>false</code> otherwise
	 * @return IEclipsePreferences[]
	 * @since 3.4 public, was added in 3.1 as private method
	 */
	public IEclipsePreferences[] getPreferenceNodes(boolean includeDefault) {
		// if the user didn't specify a search order, then return the scope that
		// this store was created on. (and optionally the default)
		if (searchContexts == null) {
			if (includeDefault) {
				return new IEclipsePreferences[] { getStorePreferences(), getDefaultPreferences() };
			}
			return new IEclipsePreferences[] { getStorePreferences() };
		}
		// otherwise the user specified a search order so return the appropriate
		// nodes based on it
		int length = searchContexts.length;
		if (includeDefault) {
			length++;
		}
		IEclipsePreferences[] preferences = new IEclipsePreferences[length];
		for (int i = 0; i < searchContexts.length; i++) {
			preferences[i] = searchContexts[i].getNode(nodeQualifier);
		}
		if (includeDefault) {
			preferences[length - 1] = getDefaultPreferences();
		}
		return preferences;
	}

	/**
	 * Set the search contexts to scopes. When searching for a value the seach will
	 * be done in the order of scope contexts and will not search the storeContext
	 * unless it is in this list.
	 * <p>
	 * If the given list is <code>null</code>, then clear this store's search
	 * contexts. This means that only this store's scope context and default scope
	 * will be used during preference value searching.
	 * </p>
	 * <p>
	 * The defaultContext will be added to the end of this list automatically and
	 * <em>MUST NOT</em> be included by the user.
	 * </p>
	 *
	 * @param scopes a list of scope contexts to use when searching, or
	 *               <code>null</code>
	 */
	public void setSearchContexts(IScopeContext[] scopes) {
		this.searchContexts = scopes;
		if (scopes == null) {
			return;
		}

		// Assert that the default was not included (we automatically add it to
		// the end)
		for (IScopeContext scope : scopes) {
			if (scope.equals(defaultContext)) {
				Assert.isTrue(false, WorkbenchMessages.ScopedPreferenceStore_DefaultAddedError);
			}
		}
	}

	@Override
	public boolean contains(String name) {
		if (name == null) {
			return false;
		}
		return (Platform.getPreferencesService().get(name, null, getPreferenceNodes(true))) != null;
	}

	@Override
	public void firePropertyChangeEvent(String name, Object oldValue, Object newValue) {
		// important: create intermediate array to protect against listeners
		// being added/removed during the notification
		final Object[] listeners = getListeners();
		if (listeners.length == 0) {
			return;
		}
		final PropertyChangeEvent event = new PropertyChangeEvent(this, name, oldValue, newValue);
		for (Object listener : listeners) {
			final IPropertyChangeListener propertyChangeListener = (IPropertyChangeListener) listener;
			SafeRunner.run(new SafeRunnable(JFaceResources.getString("PreferenceStore.changeError")) { //$NON-NLS-1$
				@Override
				public void run() {
					propertyChangeListener.propertyChange(event);
				}
			});
		}
	}

	@Override
	public boolean getBoolean(String name) {
		String value = internalGet(name);
		return value == null ? BOOLEAN_DEFAULT_DEFAULT : Boolean.parseBoolean(value);
	}

	@Override
	public boolean getDefaultBoolean(String name) {
		return getDefaultPreferences().getBoolean(name, BOOLEAN_DEFAULT_DEFAULT);
	}

	@Override
	public double getDefaultDouble(String name) {
		return getDefaultPreferences().getDouble(name, DOUBLE_DEFAULT_DEFAULT);
	}

	@Override
	public float getDefaultFloat(String name) {
		return getDefaultPreferences().getFloat(name, FLOAT_DEFAULT_DEFAULT);
	}

	@Override
	public int getDefaultInt(String name) {
		return getDefaultPreferences().getInt(name, INT_DEFAULT_DEFAULT);
	}

	@Override
	public long getDefaultLong(String name) {
		return getDefaultPreferences().getLong(name, LONG_DEFAULT_DEFAULT);
	}

	@Override
	public String getDefaultString(String name) {
		return getDefaultPreferences().get(name, STRING_DEFAULT_DEFAULT);
	}

	@Override
	public double getDouble(String name) {
		String value = internalGet(name);
		if (value == null) {
			return DOUBLE_DEFAULT_DEFAULT;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return DOUBLE_DEFAULT_DEFAULT;
		}
	}

	/**
	 * Return the string value for the specified key. Look in the nodes which are
	 * specified by this object's list of search scopes. If the value does not exist
	 * then return <code>null</code>.
	 *
	 * @param key the key to search with
	 * @return String or <code>null</code> if the value does not exist.
	 */
	private String internalGet(String key) {
		return Platform.getPreferencesService().get(key, null, getPreferenceNodes(true));
	}

	@Override
	public float getFloat(String name) {
		String value = internalGet(name);
		if (value == null) {
			return FLOAT_DEFAULT_DEFAULT;
		}
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException e) {
			return FLOAT_DEFAULT_DEFAULT;
		}
	}

	@Override
	public int getInt(String name) {
		String value = internalGet(name);
		if (value == null) {
			return INT_DEFAULT_DEFAULT;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return INT_DEFAULT_DEFAULT;
		}
	}

	@Override
	public long getLong(String name) {
		String value = internalGet(name);
		if (value == null) {
			return LONG_DEFAULT_DEFAULT;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return LONG_DEFAULT_DEFAULT;
		}
	}

	@Override
	public String getString(String name) {
		String value = internalGet(name);
		return value == null ? STRING_DEFAULT_DEFAULT : value;
	}

	@Override
	public boolean isDefault(String name) {
		if (name == null) {
			return false;
		}
		return (Platform.getPreferencesService().get(name, null, getPreferenceNodes(false))) == null;
	}

	@Override
	public boolean needsSaving() {
		return dirty;
	}

	@Override
	public void putValue(String name, String value) {
		try {
			// Do not notify listeners
			silentRunning = true;
			getStorePreferences().put(name, value);
		} finally {
			// Be sure that an exception does not stop property updates
			silentRunning = false;
			dirty = true;
		}
	}

	@Override
	public void removePropertyChangeListener(IPropertyChangeListener listener) {
		removeListenerObject(listener);
		if (!isListenerAttached()) {
			disposePreferenceStoreListener();
		}
	}

	@Override
	public void setDefault(String name, double value) {
		getDefaultPreferences().putDouble(name, value);
	}

	@Override
	public void setDefault(String name, float value) {
		getDefaultPreferences().putFloat(name, value);
	}

	@Override
	public void setDefault(String name, int value) {
		getDefaultPreferences().putInt(name, value);
	}

	@Override
	public void setDefault(String name, long value) {
		getDefaultPreferences().putLong(name, value);
	}

	@Override
	public void setDefault(String name, String defaultObject) {
		getDefaultPreferences().put(name, defaultObject);
	}

	@Override
	public void setDefault(String name, boolean value) {
		getDefaultPreferences().putBoolean(name, value);
	}

	@Override
	public void setToDefault(String name) {

		String oldValue = getString(name);
		String defaultValue = getDefaultString(name);
		try {
			silentRunning = true;// Turn off updates from the store
			// removing a non-existing preference is a no-op so call the Core
			// API directly
			getStorePreferences().remove(name);
			if (!Objects.equals(oldValue, defaultValue)) {
				dirty = true;
				firePropertyChangeEvent(name, oldValue, defaultValue);
			}

		} finally {
			silentRunning = false;// Restart listening to preferences
		}

	}

	@Override
	public void setValue(String name, double value) {
		double oldValue = getDouble(name);
		if (oldValue == value) {
			return;
		}
		try {
			silentRunning = true;// Turn off updates from the store
			if (getDefaultDouble(name) == value) {
				getStorePreferences().remove(name);
			} else {
				getStorePreferences().putDouble(name, value);
			}
			dirty = true;
			firePropertyChangeEvent(name, Double.valueOf(oldValue), Double.valueOf(value));
		} finally {
			silentRunning = false;// Restart listening to preferences
		}
	}

	@Override
	public void setValue(String name, float value) {
		float oldValue = getFloat(name);
		if (oldValue == value) {
			return;
		}
		try {
			silentRunning = true;// Turn off updates from the store
			if (getDefaultFloat(name) == value) {
				getStorePreferences().remove(name);
			} else {
				getStorePreferences().putFloat(name, value);
			}
			dirty = true;
			firePropertyChangeEvent(name, Float.valueOf(oldValue), Float.valueOf(value));
		} finally {
			silentRunning = false;// Restart listening to preferences
		}
	}

	@Override
	public void setValue(String name, int value) {
		int oldValue = getInt(name);
		if (oldValue == value) {
			return;
		}
		try {
			silentRunning = true;// Turn off updates from the store
			if (getDefaultInt(name) == value) {
				getStorePreferences().remove(name);
			} else {
				getStorePreferences().putInt(name, value);
			}
			dirty = true;
			firePropertyChangeEvent(name, Integer.valueOf(oldValue), Integer.valueOf(value));
		} finally {
			silentRunning = false;// Restart listening to preferences
		}
	}

	@Override
	public void setValue(String name, long value) {
		long oldValue = getLong(name);
		if (oldValue == value) {
			return;
		}
		try {
			silentRunning = true;// Turn off updates from the store
			if (getDefaultLong(name) == value) {
				getStorePreferences().remove(name);
			} else {
				getStorePreferences().putLong(name, value);
			}
			dirty = true;
			firePropertyChangeEvent(name, Long.valueOf(oldValue), Long.valueOf(value));
		} finally {
			silentRunning = false;// Restart listening to preferences
		}
	}

	@Override
	public void setValue(String name, String value) {
		// Do not turn on silent running here as Strings are propagated
		if (getDefaultString(name).equals(value)) {
			getStorePreferences().remove(name);
		} else {
			getStorePreferences().put(name, value);
		}
		dirty = true;
	}

	@Override
	public void setValue(String name, boolean value) {
		boolean oldValue = getBoolean(name);
		if (oldValue == value) {
			return;
		}
		try {
			silentRunning = true;// Turn off updates from the store
			if (getDefaultBoolean(name) == value) {
				getStorePreferences().remove(name);
			} else {
				getStorePreferences().putBoolean(name, value);
			}
			dirty = true;
			firePropertyChangeEvent(name, oldValue ? Boolean.TRUE : Boolean.FALSE,
					value ? Boolean.TRUE : Boolean.FALSE);
		} finally {
			silentRunning = false;// Restart listening to preferences
		}
	}

	@Override
	public void save() throws IOException {
		try {
			getStorePreferences().flush();
			dirty = false;
		} catch (BackingStoreException e) {
			throw new IOException(e.getMessage());
		}

	}

	/**
	 * Dispose the receiver.
	 */
	private void disposePreferenceStoreListener() {
		if (preferencesListener != null) {
			preferencesListener.dispose();
			preferencesListener = null;
		}
	}

	private static final class EclipsePreferencesListener
			implements IEclipsePreferences.IPreferenceChangeListener, INodeChangeListener {

		private final ScopedPreferenceStore store;
		private final IEclipsePreferences preferences;
		private final IEclipsePreferences parent;

		EclipsePreferencesListener(ScopedPreferenceStore store) {
			this.store = store;
			preferences = store.getStorePreferences();
			preferences.addPreferenceChangeListener(this);
			parent = (IEclipsePreferences) preferences.parent();
			parent.addNodeChangeListener(this);
		}

		void dispose() {
			parent.removeNodeChangeListener(this);
			preferences.removePreferenceChangeListener(this);
		}

		@Override
		public void preferenceChange(PreferenceChangeEvent event) {
			if (store.silentRunning) {
				return;
			}

			Object oldValue = event.getOldValue();
			Object newValue = event.getNewValue();
			String key = event.getKey();
			if (newValue == null) {
				newValue = store.getDefault(key, oldValue);
			} else if (oldValue == null) {
				oldValue = store.getDefault(key, newValue);
			}
			store.firePropertyChangeEvent(event.getKey(), oldValue, newValue);
		}

		@Override
		public void added(NodeChangeEvent event) {
			if (store.nodeQualifier.equals(event.getChild().name())) {
				store.getStorePreferences().addPreferenceChangeListener(this);
			}
		}

		@Override
		public void removed(NodeChangeEvent event) {
			// Do nothing as there are no events from removed node
		}

	}

}
