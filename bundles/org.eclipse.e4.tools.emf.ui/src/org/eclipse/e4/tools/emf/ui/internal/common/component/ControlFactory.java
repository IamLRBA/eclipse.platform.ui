/*******************************************************************************
 * Copyright (c) 2010 BestSolution.at and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tom Schindl <tom.schindl@bestsolution.at> - initial API and implementation
 ******************************************************************************/
package org.eclipse.e4.tools.emf.ui.internal.common.component;

import org.eclipse.jface.databinding.swt.IWidgetValueProperty;
import org.eclipse.jface.databinding.swt.WidgetProperties;

import org.eclipse.emf.edit.domain.EditingDomain;

import org.eclipse.e4.tools.emf.ui.internal.common.ModelEditor;

import org.eclipse.e4.tools.emf.ui.internal.common.component.dialogs.FindImportElementDialog;

import org.eclipse.core.databinding.observable.list.IObservableList;

import org.eclipse.e4.tools.emf.ui.common.Util;

import org.eclipse.emf.ecore.EObject;

import org.eclipse.e4.ui.model.application.ui.MUILabel;

import org.eclipse.e4.ui.workbench.UIEvents.UILabel;

import org.eclipse.core.databinding.observable.map.IObservableMap;

import org.eclipse.jface.databinding.viewers.ObservableMapLabelProvider;

import org.eclipse.emf.databinding.EMFDataBindingContext;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.observable.value.IValueChangeListener;
import org.eclipse.core.databinding.observable.value.ValueChangeEvent;
import org.eclipse.emf.databinding.edit.IEMFEditValueProperty;
import org.eclipse.jface.databinding.viewers.IViewerValueProperty;
import org.eclipse.jface.databinding.viewers.ViewerProperties;
import org.eclipse.jface.databinding.viewers.ViewerSupport;
import org.eclipse.jface.viewers.ComboViewer;

import org.eclipse.e4.ui.model.application.commands.MBindings;

import org.eclipse.e4.ui.model.application.ui.MContext;

import java.util.Arrays;
import java.util.List;
import org.eclipse.e4.tools.emf.ui.common.component.AbstractComponentEditor;
import org.eclipse.e4.tools.emf.ui.internal.Messages;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.MApplicationElement;
import org.eclipse.e4.ui.model.application.commands.impl.CommandsPackageImpl;
import org.eclipse.e4.ui.model.application.impl.ApplicationPackageImpl;
import org.eclipse.e4.ui.model.application.ui.impl.UiPackageImpl;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.databinding.EMFProperties;
import org.eclipse.emf.databinding.IEMFListProperty;
import org.eclipse.emf.databinding.edit.EMFEditProperties;
import org.eclipse.emf.databinding.edit.IEMFEditListProperty;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.MoveCommand;
import org.eclipse.emf.edit.command.RemoveCommand;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class ControlFactory {
	public static void createFindImport(Composite parent, final AbstractComponentEditor editor, EMFDataBindingContext context) {
		IWidgetValueProperty textProp = WidgetProperties.text(SWT.Modify);

		Label l = new Label(parent, SWT.NONE);
		l.setText("Reference-Id");
		l.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

		Text t = new Text(parent, SWT.BORDER);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		t.setLayoutData(gd);
		context.bindValue(textProp.observeDelayed(200, t), EMFEditProperties.value(editor.getEditingDomain(), ApplicationPackageImpl.Literals.APPLICATION_ELEMENT__ELEMENT_ID).observeDetail(editor.getMaster()));

		final Button b = new Button(parent, SWT.PUSH | SWT.FLAT);
		b.setText("Find ...");
		b.setImage(editor.getImage(t.getDisplay(), AbstractComponentEditor.SEARCH_IMAGE));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FindImportElementDialog dialog = new FindImportElementDialog(b.getShell(), editor.getEditingDomain(), (EObject) editor.getMaster().getValue());
				dialog.open();
			}
		});
	}

	public static void createSelectedElement(Composite parent, final AbstractComponentEditor editor, final EMFDataBindingContext context, String label) {
		Label l = new Label(parent, SWT.NONE);
		l.setText(label);
		l.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

		ComboViewer viewer = new ComboViewer(parent);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		viewer.getControl().setLayoutData(gd);
		IEMFEditListProperty listProp = EMFEditProperties.list(editor.getEditingDomain(), UiPackageImpl.Literals.ELEMENT_CONTAINER__CHILDREN);
		IEMFEditValueProperty labelProp = EMFEditProperties.value(editor.getEditingDomain(), UiPackageImpl.Literals.UI_LABEL__LABEL);
		IEMFEditValueProperty idProp = EMFEditProperties.value(editor.getEditingDomain(), ApplicationPackageImpl.Literals.APPLICATION_ELEMENT__ELEMENT_ID);

		IViewerValueProperty vProp = ViewerProperties.singleSelection();

		final Binding[] binding = new Binding[1];
		final IObservableValue uiObs = vProp.observe(viewer);
		final IObservableValue mObs = EMFEditProperties.value(editor.getEditingDomain(), UiPackageImpl.Literals.ELEMENT_CONTAINER__SELECTED_ELEMENT).observeDetail(editor.getMaster());
		editor.getMaster().addValueChangeListener(new IValueChangeListener() {

			public void handleValueChange(ValueChangeEvent event) {
				if (binding[0] != null) {
					binding[0].dispose();
				}

			}
		});

		final IObservableList list = listProp.observeDetail(editor.getMaster());
		ObservableListContentProvider cp = new ObservableListContentProvider();
		viewer.setContentProvider(cp);
		IObservableMap[] attributeMaps = { labelProp.observeDetail(cp.getKnownElements()), idProp.observeDetail(cp.getKnownElements()) };
		viewer.setLabelProvider(new ObservableMapLabelProvider(attributeMaps) {
			@Override
			public String getText(Object element) {
				EObject o = (EObject) element;
				String rv = o.eClass().getName();

				if (element instanceof MUILabel) {
					MUILabel label = (MUILabel) element;
					if (!Util.isNullOrEmpty(label.getLabel())) {
						return rv + " - " + label.getLabel().trim();
					}

				}

				if (element instanceof MApplicationElement) {
					MApplicationElement appEl = (MApplicationElement) element;
					if (!Util.isNullOrEmpty(appEl.getElementId())) {
						return rv + " - " + appEl.getElementId();
					}
				}

				return rv + "[" + list.indexOf(element) + "]";
			}
		});
		viewer.setInput(list);

		editor.getMaster().addValueChangeListener(new IValueChangeListener() {

			public void handleValueChange(ValueChangeEvent event) {
				binding[0] = context.bindValue(uiObs, mObs);
			}
		});
	}

	public static void createStringListWidget(Composite parent, final AbstractComponentEditor editor, String label, final EStructuralFeature feature) {
		Label l = new Label(parent, SWT.NONE);
		l.setText(label);
		l.setLayoutData(new GridData(GridData.END,GridData.BEGINNING,false,false));

		final Text t = new Text(parent, SWT.BORDER);
		t.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		t.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
					handleAddText(editor, UiPackageImpl.Literals.CONTEXT__VARIABLES, t);
				}
			}
		});

		Button b = new Button(parent, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Add);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.TABLE_ADD_IMAGE));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, false, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleAddText(editor, feature, t);
			}
		});

		new Label(parent, SWT.NONE);

		final TableViewer viewer = new TableViewer(parent);
		viewer.setLabelProvider(new LabelProvider());
		viewer.setContentProvider(new ObservableListContentProvider());
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.heightHint = 150;
		viewer.getControl().setLayoutData(gd);

		IEMFListProperty prop = EMFProperties.list(feature);
		viewer.setInput(prop.observeDetail(editor.getMaster()));

		Composite buttonComp = new Composite(parent, SWT.NONE);
		buttonComp.setLayoutData(new GridData(GridData.FILL, GridData.END, false, false));
		GridLayout gl = new GridLayout();
		gl.marginLeft = 0;
		gl.marginRight = 0;
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		buttonComp.setLayout(gl);

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Up);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.ARROW_UP));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					IStructuredSelection s = (IStructuredSelection) viewer.getSelection();
					if (s.size() == 1) {
						Object obj = s.getFirstElement();
						MContext container = (MContext) editor.getMaster().getValue();
						int idx = container.getVariables().indexOf(obj) - 1;
						if (idx >= 0) {
							Command cmd = MoveCommand.create(editor.getEditingDomain(), editor.getMaster().getValue(), feature, obj, idx);

							if (cmd.canExecute()) {
								editor.getEditingDomain().getCommandStack().execute(cmd);
								viewer.setSelection(new StructuredSelection(obj));
							}
						}

					}
				}
			}
		});

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Down);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.ARROW_DOWN));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					IStructuredSelection s = (IStructuredSelection) viewer.getSelection();
					if (s.size() == 1) {
						Object obj = s.getFirstElement();
						MContext container = (MApplication) editor.getMaster().getValue();
						int idx = container.getVariables().indexOf(obj) + 1;
						if (idx < container.getVariables().size()) {
							Command cmd = MoveCommand.create(editor.getEditingDomain(), editor.getMaster().getValue(), feature, obj, idx);

							if (cmd.canExecute()) {
								editor.getEditingDomain().getCommandStack().execute(cmd);
								viewer.setSelection(new StructuredSelection(obj));
							}
						}

					}
				}
			}
		});

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Remove);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.TABLE_DELETE_IMAGE));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					MContext el = (MContext) editor.getMaster().getValue();
					List<?> ids = ((IStructuredSelection) viewer.getSelection()).toList();
					Command cmd = RemoveCommand.create(editor.getEditingDomain(), el, feature, ids);
					if (cmd.canExecute()) {
						editor.getEditingDomain().getCommandStack().execute(cmd);
						if (el.getVariables().size() > 0) {
							viewer.setSelection(new StructuredSelection(el.getVariables().get(0)));
						}
					}
				}
			}
		});
	}

	public static void createVariablesWidget(Composite parent, final AbstractComponentEditor editor) {
		Label l = new Label(parent, SWT.NONE);
		l.setText(Messages.ControlFactory_ContextVariables);
		l.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

		final Text t = new Text(parent, SWT.BORDER);
		t.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		t.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
					handleAddText(editor, UiPackageImpl.Literals.CONTEXT__VARIABLES, t);
				}
			}
		});

		Button b = new Button(parent, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Add);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.TABLE_ADD_IMAGE));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, false, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleAddText(editor, UiPackageImpl.Literals.CONTEXT__VARIABLES, t);
			}
		});

		new Label(parent, SWT.NONE);

		final TableViewer viewer = new TableViewer(parent);
		viewer.setLabelProvider(new LabelProvider());
		viewer.setContentProvider(new ObservableListContentProvider());
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.heightHint = 150;
		viewer.getControl().setLayoutData(gd);

		IEMFListProperty prop = EMFProperties.list(UiPackageImpl.Literals.CONTEXT__VARIABLES);
		viewer.setInput(prop.observeDetail(editor.getMaster()));

		Composite buttonComp = new Composite(parent, SWT.NONE);
		buttonComp.setLayoutData(new GridData(GridData.FILL, GridData.END, false, false));
		GridLayout gl = new GridLayout();
		gl.marginLeft = 0;
		gl.marginRight = 0;
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		buttonComp.setLayout(gl);

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Up);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.ARROW_UP));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					IStructuredSelection s = (IStructuredSelection) viewer.getSelection();
					if (s.size() == 1) {
						Object obj = s.getFirstElement();
						MContext container = (MContext) editor.getMaster().getValue();
						int idx = container.getVariables().indexOf(obj) - 1;
						if (idx >= 0) {
							Command cmd = MoveCommand.create(editor.getEditingDomain(), editor.getMaster().getValue(), UiPackageImpl.Literals.CONTEXT__VARIABLES, obj, idx);

							if (cmd.canExecute()) {
								editor.getEditingDomain().getCommandStack().execute(cmd);
								viewer.setSelection(new StructuredSelection(obj));
							}
						}

					}
				}
			}
		});

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Down);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.ARROW_DOWN));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					IStructuredSelection s = (IStructuredSelection) viewer.getSelection();
					if (s.size() == 1) {
						Object obj = s.getFirstElement();
						MContext container = (MApplication) editor.getMaster().getValue();
						int idx = container.getVariables().indexOf(obj) + 1;
						if (idx < container.getVariables().size()) {
							Command cmd = MoveCommand.create(editor.getEditingDomain(), editor.getMaster().getValue(), UiPackageImpl.Literals.CONTEXT__VARIABLES, obj, idx);

							if (cmd.canExecute()) {
								editor.getEditingDomain().getCommandStack().execute(cmd);
								viewer.setSelection(new StructuredSelection(obj));
							}
						}

					}
				}
			}
		});

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Remove);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.TABLE_DELETE_IMAGE));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					MContext el = (MContext) editor.getMaster().getValue();
					List<?> ids = ((IStructuredSelection) viewer.getSelection()).toList();
					Command cmd = RemoveCommand.create(editor.getEditingDomain(), el, UiPackageImpl.Literals.CONTEXT__VARIABLES, ids);
					if (cmd.canExecute()) {
						editor.getEditingDomain().getCommandStack().execute(cmd);
						if (el.getVariables().size() > 0) {
							viewer.setSelection(new StructuredSelection(el.getVariables().get(0)));
						}
					}
				}
			}
		});
	}

	public static void createBindingsWidget(Composite parent, final AbstractComponentEditor editor) {
		Label l = new Label(parent, SWT.NONE);
		l.setText(Messages.ControlFactory_BindingContexts);
		l.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

		final Text t = new Text(parent, SWT.BORDER);
		t.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		t.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
					handleAddText(editor, CommandsPackageImpl.Literals.BINDINGS__BINDING_CONTEXTS, t);
				}
			}
		});

		Button b = new Button(parent, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Add);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.TABLE_ADD_IMAGE));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, false, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleAddText(editor, CommandsPackageImpl.Literals.BINDINGS__BINDING_CONTEXTS, t);
			}
		});

		new Label(parent, SWT.NONE);

		final TableViewer viewer = new TableViewer(parent);
		viewer.setLabelProvider(new LabelProvider());
		viewer.setContentProvider(new ObservableListContentProvider());
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.heightHint = 150;
		viewer.getControl().setLayoutData(gd);

		IEMFListProperty prop = EMFProperties.list(CommandsPackageImpl.Literals.BINDINGS__BINDING_CONTEXTS);
		viewer.setInput(prop.observeDetail(editor.getMaster()));

		Composite buttonComp = new Composite(parent, SWT.NONE);
		buttonComp.setLayoutData(new GridData(GridData.FILL, GridData.END, false, false));
		GridLayout gl = new GridLayout();
		gl.marginLeft = 0;
		gl.marginRight = 0;
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		buttonComp.setLayout(gl);

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Up);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.ARROW_UP));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					IStructuredSelection s = (IStructuredSelection) viewer.getSelection();
					if (s.size() == 1) {
						Object obj = s.getFirstElement();
						MBindings container = (MBindings) editor.getMaster().getValue();
						int idx = container.getBindingContexts().indexOf(obj) - 1;
						if (idx >= 0) {
							Command cmd = MoveCommand.create(editor.getEditingDomain(), editor.getMaster().getValue(), CommandsPackageImpl.Literals.BINDINGS__BINDING_CONTEXTS, obj, idx);

							if (cmd.canExecute()) {
								editor.getEditingDomain().getCommandStack().execute(cmd);
								viewer.setSelection(new StructuredSelection(obj));
							}
						}

					}
				}
			}
		});

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Down);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.ARROW_DOWN));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					IStructuredSelection s = (IStructuredSelection) viewer.getSelection();
					if (s.size() == 1) {
						Object obj = s.getFirstElement();
						MBindings container = (MBindings) editor.getMaster().getValue();
						int idx = container.getBindingContexts().indexOf(obj) + 1;
						if (idx < container.getBindingContexts().size()) {
							Command cmd = MoveCommand.create(editor.getEditingDomain(), editor.getMaster().getValue(), CommandsPackageImpl.Literals.BINDINGS__BINDING_CONTEXTS, obj, idx);

							if (cmd.canExecute()) {
								editor.getEditingDomain().getCommandStack().execute(cmd);
								viewer.setSelection(new StructuredSelection(obj));
							}
						}

					}
				}
			}
		});

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Remove);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.TABLE_DELETE_IMAGE));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!viewer.getSelection().isEmpty()) {
					MBindings el = (MBindings) editor.getMaster().getValue();
					List<?> ids = ((IStructuredSelection) viewer.getSelection()).toList();
					Command cmd = RemoveCommand.create(editor.getEditingDomain(), el, CommandsPackageImpl.Literals.BINDINGS__BINDING_CONTEXTS, ids);
					if (cmd.canExecute()) {
						editor.getEditingDomain().getCommandStack().execute(cmd);
						if (el.getBindingContexts().size() > 0) {
							viewer.setSelection(new StructuredSelection(el.getBindingContexts().get(0)));
						}
					}
				}
			}
		});
	}

	public static void createTagsWidget(Composite parent, final AbstractComponentEditor editor) {
		Label l = new Label(parent, SWT.NONE);
		l.setText(Messages.ControlFactory_Tags);
		l.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

		final Text tagText = new Text(parent, SWT.BORDER);
		tagText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		tagText.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
					handleAddText(editor, ApplicationPackageImpl.Literals.APPLICATION_ELEMENT__TAGS, tagText);
				}
			}
		});

		Button b = new Button(parent, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Add);
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleAddText(editor, ApplicationPackageImpl.Literals.APPLICATION_ELEMENT__TAGS, tagText);
			}
		});
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.TABLE_ADD_IMAGE));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, false, false));

		new Label(parent, SWT.NONE);
		final TableViewer viewer = new TableViewer(parent);
		viewer.setContentProvider(new ObservableListContentProvider());
		viewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element.toString();
			}
		});
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.heightHint = 120;
		viewer.getControl().setLayoutData(gd);

		IEMFEditListProperty prop = EMFEditProperties.list(editor.getEditingDomain(), ApplicationPackageImpl.Literals.APPLICATION_ELEMENT__TAGS);
		viewer.setInput(prop.observeDetail(editor.getMaster()));

		Composite buttonComp = new Composite(parent, SWT.NONE);
		buttonComp.setLayoutData(new GridData(GridData.FILL, GridData.END, false, false));
		GridLayout gl = new GridLayout();
		gl.marginLeft = 0;
		gl.marginRight = 0;
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		buttonComp.setLayout(gl);

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Up);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.ARROW_UP));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Down);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.ARROW_DOWN));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));

		b = new Button(buttonComp, SWT.PUSH | SWT.FLAT);
		b.setText(Messages.ControlFactory_Remove);
		b.setImage(editor.getImage(b.getDisplay(), AbstractComponentEditor.TABLE_DELETE_IMAGE));
		b.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IStructuredSelection s = (IStructuredSelection) viewer.getSelection();
				if (!s.isEmpty()) {
					MApplicationElement appEl = (MApplicationElement) editor.getMaster().getValue();
					Command cmd = RemoveCommand.create(editor.getEditingDomain(), appEl, ApplicationPackageImpl.Literals.APPLICATION_ELEMENT__TAGS, s.toList());
					if (cmd.canExecute()) {
						editor.getEditingDomain().getCommandStack().execute(cmd);
					}
				}
			}
		});
	}

	private static void handleAddText(AbstractComponentEditor editor, EStructuralFeature feature, Text tagText) {
		if (tagText.getText().trim().length() > 0) {
			String[] tags = tagText.getText().split(";"); //$NON-NLS-1$
			for (int i = 0; i < tags.length; i++) {
				tags[i] = tags[i].trim();
			}

			MApplicationElement appEl = (MApplicationElement) editor.getMaster().getValue();
			Command cmd = AddCommand.create(editor.getEditingDomain(), appEl, feature, Arrays.asList(tags));
			if (cmd.canExecute()) {
				editor.getEditingDomain().getCommandStack().execute(cmd);
			}
			tagText.setText(""); //$NON-NLS-1$
		}
	}
}