/*********************************************************************************************
 *
 * 'SyntaxErrorsView.java, in plugin ummisco.gama.ui.modeling, is part of the source code of the GAMA modeling and
 * simulation platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package ummisco.gama.ui.views;

import java.util.HashMap;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.ISources;
import org.eclipse.ui.internal.views.markers.ConfigureContentsDialogHandler;
import org.eclipse.ui.views.markers.MarkerSupportView;

import msi.gama.common.GamaPreferences;
import msi.gama.common.GamaPreferences.IPreferenceChangeListener;
import msi.gama.lang.gaml.indexer.GamlResourceIndexer;
import ummisco.gama.ui.commands.RefreshHandler;
import ummisco.gama.ui.resources.IGamaColors;
import ummisco.gama.ui.views.toolbar.GamaToolbar2;
import ummisco.gama.ui.views.toolbar.GamaToolbarFactory;
import ummisco.gama.ui.views.toolbar.IToolbarDecoratedView;

public class SyntaxErrorsView extends MarkerSupportView implements IToolbarDecoratedView {

	protected Composite parent;
	protected GamaToolbar2 toolbar;

	ToolItem warningAction, infoAction;
	final BuildPreferenceChangeListener listener;

	public SyntaxErrorsView() {
		super("msi.gama.lang.gaml.ui.error.generator");
		listener = new BuildPreferenceChangeListener(this);
		GamaPreferences.WARNINGS_ENABLED.addChangeListener(listener);
		GamaPreferences.INFO_ENABLED.addChangeListener(listener);
	}

	@Override
	public void createPartControl(final Composite compo) {
		this.parent = GamaToolbarFactory.createToolbars(this, compo);
		super.createPartControl(parent);
	}

	@Override
	public void dispose() {
		super.dispose();
		GamaPreferences.WARNINGS_ENABLED.removeChangeListener(listener);
		GamaPreferences.INFO_ENABLED.removeChangeListener(listener);
	}

	public static class BuildPreferenceChangeListener implements IPreferenceChangeListener<Boolean> {

		SyntaxErrorsView view;

		BuildPreferenceChangeListener(final SyntaxErrorsView v) {
			view = v;
		}

		/**
		 * @see msi.gama.common.GamaPreferences.IPreferenceChangeListener#beforeValueChange(java.lang.Object)
		 */
		@Override
		public boolean beforeValueChange(final Boolean newValue) {
			return true;
		}

		/**
		 * @see msi.gama.common.GamaPreferences.IPreferenceChangeListener#afterValueChange(java.lang.Object)
		 */
		@Override
		public void afterValueChange(final Boolean newValue) {

			try {
				ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.CLEAN_BUILD, null);
				view.checkActions();
			} catch (final CoreException e) {}

		}
	}

	void checkActions() {
		if (warningAction != null) {
			warningAction.setSelection(GamaPreferences.WARNINGS_ENABLED.getValue());
		}
		if (infoAction != null) {
			infoAction.setSelection(GamaPreferences.INFO_ENABLED.getValue());
		}
	}

	@Override
	protected void setContentDescription(final String description) {
		toolbar.status((Image) null, description, new SelectionAdapter() {

			@Override
			public void widgetSelected(final SelectionEvent e) {
				openFilterDialog();
			}

		}, IGamaColors.BLUE, SWT.LEFT);
	}

	@Override
	public void createToolItems(final GamaToolbar2 tb) {
		this.toolbar = tb;

		warningAction = tb.check("build.warnings2", "", "Toggle display of warning markers", new SelectionAdapter() {

			@Override
			public void widgetSelected(final SelectionEvent e) {
				final boolean b = ((ToolItem) e.widget).getSelection();
				GamaPreferences.WARNINGS_ENABLED.set(b).save();
			}
		}, SWT.RIGHT);
		warningAction.setSelection(GamaPreferences.WARNINGS_ENABLED.getValue());

		infoAction = tb.check("build.infos2", "", "Toggle display of information markers", new SelectionAdapter() {

			@Override
			public void widgetSelected(final SelectionEvent e) {
				final boolean b = ((ToolItem) e.widget).getSelection();
				GamaPreferences.INFO_ENABLED.set(b).save();
			}
		}, SWT.RIGHT);
		infoAction.setSelection(GamaPreferences.INFO_ENABLED.getValue());

		tb.sep(GamaToolbarFactory.TOOLBAR_SEP, SWT.RIGHT);
		tb.button("build.all2", "", "Clean and validate all projects", new SelectionAdapter() {

			@Override
			public void widgetSelected(final SelectionEvent e) {
				final IWorkspace workspace = ResourcesPlugin.getWorkspace();
				try {
					GamlResourceIndexer.eraseIndex();
					workspace.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor() {

						@Override
						public void done() {
							super.done();
							RefreshHandler.run(workspace.getRoot());

						}

					});

				} catch (final CoreException ex) {
					ex.printStackTrace();
				}

			}

		}, SWT.RIGHT);

	}

	void openFilterDialog() {
		final IEvaluationContext ec = new EvaluationContext(null, this);
		ec.addVariable(ISources.ACTIVE_PART_NAME, this);
		final ExecutionEvent ev = new ExecutionEvent(null, new HashMap<>(), this, ec);
		new ConfigureContentsDialogHandler().execute(ev);
	}
}
