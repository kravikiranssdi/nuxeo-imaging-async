package org.nuxeo.ecm.platform.picture.async;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitFilteringEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;

/**
 * Async listener, triggered by the {@link PictureChangedListener}.
 * Schedule the conversion work.
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 *
 */
public class ConversionWorkTrigger implements PostCommitFilteringEventListener {

    public static final String TRIGGER_EVENT ="updatePictureViews";
    public static final String XPATH_PROPERTY = "PictureViewSourceXpath";

    @Override
    public void handleEvent(EventBundle bundle) throws ClientException {
        for (Event event : bundle) {
            if (TRIGGER_EVENT.equals(event.getName())) {
                DocumentEventContext docCtx = (DocumentEventContext) event.getContext();

                DocumentModel doc = docCtx.getSourceDocument();
                String xpath = (String) docCtx.getProperty(XPATH_PROPERTY);
                if (xpath == null) {
                    xpath = "file:content";
                }
                WorkManager wm = Framework.getLocalService(WorkManager.class);
                wm.schedule(new PictureViewsComputerWorker(
                        doc.getRepositoryName(), doc.getRef(), xpath));
            }
        }
    }

    @Override
    public boolean acceptEvent(Event event) {
        return (TRIGGER_EVENT.equals(event.getName()));
    }

}
