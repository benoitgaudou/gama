/*********************************************************************************************
 *
 * 'GamlDocumentProvider.java, in plugin ummisco.gama.ui.modeling, is part of the source code of the
 * GAMA modeling and simulation platform.
 * (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package msi.gama.lang.gaml.ui.editor;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.model.XtextDocument;
import org.eclipse.xtext.ui.editor.model.XtextDocumentProvider;
import org.eclipse.xtext.ui.editor.quickfix.IssueResolutionProvider;
import org.eclipse.xtext.ui.editor.validation.AnnotationIssueProcessor;
import org.eclipse.xtext.ui.editor.validation.IValidationIssueProcessor;
import org.eclipse.xtext.ui.editor.validation.ValidationJob;
import org.eclipse.xtext.util.concurrent.IReadAccess;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;

import com.google.inject.Inject;

/**
 * The class GamlDocumentProvider.
 *
 * @author drogoul
 * @since 31 août 2016
 *
 */
public class GamlDocumentProvider extends XtextDocumentProvider {

	@Inject private IssueResolutionProvider issueResolutionProvider;

	@Inject private IResourceValidator resourceValidator;

	private XtextDocument doc;

	class GamlValidationJob extends ValidationJob {

		public GamlValidationJob(final IResourceValidator xtextResourceChecker,
				final IReadAccess<XtextResource> xtextDocument,
				final IValidationIssueProcessor validationIssueProcessor, final CheckMode checkMode) {
			super(xtextResourceChecker, xtextDocument, validationIssueProcessor, checkMode);

		}

		@Override
		public final List<Issue> createIssues(final IProgressMonitor monitor) {
			// if (!indexer.isReady()) {
			// doc.readOnly(new IUnitOfWork.Void<XtextResource>() {
			//
			// @Override
			// public void process(final XtextResource state) throws Exception {
			// state.setValidationDisabled(true);
			// }
			// });
			// indexer.waitToBeReady();
			// doc.readOnly(new IUnitOfWork.Void<XtextResource>() {
			//
			// @Override
			// public void process(final XtextResource state) throws Exception {
			// state.setValidationDisabled(false);
			// }
			// });
			// }

			return super.createIssues(monitor);
		}

	}

	@Override
	public void registerAnnotationInfoProcessor(final ElementInfo info) {
		doc = (XtextDocument) info.fDocument;
		if (info.fModel != null) {
			final AnnotationIssueProcessor annotationIssueProcessor = new AnnotationIssueProcessor(doc, info.fModel,
					issueResolutionProvider);
			final ValidationJob job = new GamlValidationJob(resourceValidator, doc, annotationIssueProcessor,
					CheckMode.FAST_ONLY);
			doc.setValidationJob(job);
		}

	}

}
