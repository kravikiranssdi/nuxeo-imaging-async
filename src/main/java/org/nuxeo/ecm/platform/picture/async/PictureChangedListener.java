/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.platform.picture.async;

import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_FACET;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.picture.api.adapters.AbstractPictureAdapter;
import org.nuxeo.runtime.api.Framework;

/**
 * Listener overriding the default listener : instead of running the actual
 * conversion, it simply fires an event to schedule an async listener.
 *
 * Going through an async listener to trigger the conversion worker is needed to
 * ensure that at the time the worker is run the Document has be
 * created/saved/commited.
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 *
 */
public class PictureChangedListener implements EventListener {

    public static final String DISABLE_PICTURECHANGE_LISTENER = "disablePictureChangeListener";

    @Override
    public void handleEvent(Event event) throws ClientException {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        Boolean block = (Boolean) event.getContext().getProperty(
                DISABLE_PICTURECHANGE_LISTENER);
        if (block != null && block) {
            // ignore the event - we are blocked by the caller
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;

        if (needsRecompute(docCtx, event.getName())) {
            triggerConversion(docCtx);
        }

    }

    protected boolean needsRecompute(DocumentEventContext docCtx,
            String eventName) throws ClientException {
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc.hasFacet(PICTURE_FACET)) {
            Property fileProp = doc.getProperty("file:content");
            if (DocumentEventTypes.DOCUMENT_CREATED.equals(eventName)
                    && fileProp != null && fileProp.getValue() != null) {
                // no need to check for dirty fields
                return true;
            }
            Property viewsProp = doc.getProperty(AbstractPictureAdapter.VIEWS_PROPERTY);
            if (fileProp.isDirty()) {
                // if the views are dirty, assume they're up to date
                if (viewsProp == null || !viewsProp.isDirty()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void triggerConversion(DocumentEventContext docCtx)
            throws ClientException {
        docCtx.getProperties().put(ConversionWorkTrigger.XPATH_PROPERTY,
                "file:content");
        Event trigger = docCtx.newEvent(ConversionWorkTrigger.TRIGGER_EVENT);
        EventService eventService = Framework.getLocalService(EventService.class);
        eventService.fireEvent(trigger);
    }
}
