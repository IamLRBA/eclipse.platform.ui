/*******************************************************************************
 * Copyright (c) 2006, 2017 IBM Corporation and others.
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
 *     Dina Sayed, dsayed@eg.ibm.com, IBM -  bug 269844
 *     Andrey Loskutov <loskutov@gmx.de> - generified interface, bug 462760
 *     Mickael Istria (Red Hat Inc.) - Bug 486901
 *     Lucas Bullen (Red Hat Inc.) - Bug 522096 - "Close Projects" on working set
 *******************************************************************************/
package org.eclipse.ui.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDEActionFactory;
import org.eclipse.ui.internal.ide.IDEInternalPreferences;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.eclipse.ui.internal.ide.misc.DisjointSet;
import org.eclipse.core.runtime.jobs.Job;

/**
 * This action closes all projects that are unrelated to the selected projects. A
 * project is unrelated if it is not directly or transitively referenced by one
 * of the selected projects, and does not directly or transitively reference
 * one of the selected projects.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @see IDEActionFactory#CLOSE_UNRELATED_PROJECTS
 * @since 3.3
 */
public class CloseUnrelatedProjectsAction extends CloseResourceAction {
	/**
	 * The id of this action.
	 */
	@SuppressWarnings("hiding")
	public static final String ID = PlatformUI.PLUGIN_ID + ".CloseUnrelatedProjectsAction"; //$NON-NLS-1$

	private List<IResource> projectsToClose = new ArrayList<>();

	private boolean selectionDirty = true;

	private List<? extends IResource> oldSelection = Collections.emptyList();

	/**
	 * Builds the connected component set for the input projects.
	 * The result is a DisjointSet where all related projects belong
	 * to the same set.
	 */
	private static void buildConnectedComponentsInBackground(IProject[] projects, Runnable onComplete) {
		Job job = new Job("Compute Connected Components") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				DisjointSet<IProject> set = new DisjointSet<>();
				for (IProject project : projects) {
					set.makeSet(project);
				}
				for (IProject project : projects) {
					try {
						IProject[] references = project.getReferencedProjects();
						for (IProject reference : references) {
							IProject setOne = set.findSet(project);
							IProject setTwo = set.findSet(reference);
							if (setOne != null && setTwo != null && setOne != setTwo) {
								set.union(setOne, setTwo);
							}
						}
					} catch (CoreException e) {
						// Assume inaccessible projects have no references
					}
				}
				Display.getDefault().asyncExec(onComplete);
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.LONG);
		job.schedule();
	}

	/**
	 * Creates this action.
	 *
	 * @param shell
	 *            The shell to use for parenting any dialogs created by this
	 *            action.
	 *
	 * @deprecated {@link #CloseUnrelatedProjectsAction(IShellProvider)}
	 */
	@Deprecated
	public CloseUnrelatedProjectsAction(Shell shell) {
		super(shell, IDEWorkbenchMessages.CloseUnrelatedProjectsAction_text);
		initAction();
	}

	/**
	 * Creates this action.
	 *
	 * @param provider
	 *            The shell to use for parenting any dialogs created by this
	 *            action.
	 * @since 3.4
	 */
	public CloseUnrelatedProjectsAction(IShellProvider provider) {
		super(provider, IDEWorkbenchMessages.CloseUnrelatedProjectsAction_text,
				IDEWorkbenchMessages.CloseUnrelatedProjectsAction_toolTip,
				IDEWorkbenchMessages.CloseUnrelatedProjectsAction_text_plural,
				IDEWorkbenchMessages.CloseUnrelatedProjectsAction_toolTip_plural);
		initAction();
	}

	@Override
	public void run() {
		if (promptForConfirmation()) {
			super.run();
		}
	}

	/**
	 * Returns whether to close unrelated projects.
	 * Consults the preference and prompts the user if necessary.
	 *
	 * @return <code>true</code> if unrelated projects should be closed, and
	 *         <code>false</code> otherwise.
	 */
	private boolean promptForConfirmation() {
		IPreferenceStore store = IDEWorkbenchPlugin.getDefault().getPreferenceStore();
		if (store.getBoolean(IDEInternalPreferences.CLOSE_UNRELATED_PROJECTS)) {
			return true;
		}

		List<? extends IResource> selection = super.getSelectedResources();
		int selectionSize = selection.size();
		if (selectionSize == 0) {
			return true;
		}

		String message = null;
		if (selectionSize == 1) {
			IResource firstSelected = selection.get(0);
			String projectName = (firstSelected instanceof IProject) ? firstSelected.getName() : null;
			message = NLS.bind(IDEWorkbenchMessages.CloseUnrelatedProjectsAction_confirmMsg1, projectName);
		} else {
			message = NLS.bind(IDEWorkbenchMessages.CloseUnrelatedProjectsAction_confirmMsgN, selectionSize);
		}

		MessageDialogWithToggle dialog = MessageDialogWithToggle.openOkCancelConfirm(
				getShell(), IDEWorkbenchMessages.CloseUnrelatedProjectsAction_toolTip,
				message, IDEWorkbenchMessages.CloseUnrelatedProjectsAction_AlwaysClose,
				false, null, null);

		if (dialog.getReturnCode() != IDialogConstants.OK_ID) {
			return false;
		}

		store.setValue(IDEInternalPreferences.CLOSE_UNRELATED_PROJECTS, dialog.getToggleState());
		return true;
	}

	/**
	 * Initializes for the constructor.
	 */
	private void initAction() {
		setId(ID);
		setToolTipText(IDEWorkbenchMessages.CloseUnrelatedProjectsAction_toolTip);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IIDEHelpContextIds.CLOSE_UNRELATED_PROJECTS_ACTION);
	}

	@Override
	protected void clearCache() {
		super.clearCache();
		oldSelection = Collections.emptyList();
		selectionDirty = true;
	}

	@Override
	protected List<? extends IResource> getSelectedResources() {
		if (selectionDirty) {
			List<? extends IResource> newSelection = super.getSelectedResources();
			if (!oldSelection.equals(newSelection)) {
				oldSelection = newSelection;
				projectsToClose = computeRelated(newSelection);
			}
			selectionDirty = false;
		}
		return projectsToClose;
	}

	private List<IResource> computeRelated(List<? extends IResource> selection) {
		if (selection.contains(ResourcesPlugin.getWorkspace().getRoot())) {
			return new ArrayList<>();
		}
		List<IResource> projects = new ArrayList<>();
		buildConnectedComponentsInBackground(ResourcesPlugin.getWorkspace().getRoot().getProjects(), () -> {
			DisjointSet<IProject> set = buildConnectedComponents(ResourcesPlugin.getWorkspace().getRoot().getProjects());
			for (IResource resource : selection) {
				IProject project = resource.getProject();
				if (project != null) {
					set.removeSet(project);
				}
			}
			set.toList(projects);
		});
		return projects;
	}
}
