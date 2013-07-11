package org.nuxeo.ecm.platform.picture.async;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.platform.picture.api.adapters.PictureResourceAdapter;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;
/**
 * Worker that handles conversion processing.
 *
 * Actual processing code is inside the {@link PictureResourceAdapter}, but the worker splits the works in 3 phases :
 *
 * <ul>
 *   <li>Fetch Document and data: inside a transaction</li>
 *   <li>Run the conversions : ouside of TX, running on detached {@link DocumentModel}</li>
 *   <li>Save Document with new views : inside a transaction</li>
 * </ul>
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 *
 */

public class PictureViewsComputerWorker extends AbstractWork {

    protected static final Log log = LogFactory.getLog(PictureViewsComputerWorker.class);

    protected final String repoName;

    protected final DocumentRef ref;

    protected final String xpath;

    protected final int retry;

    protected LoginContext loginContext;

    protected CoreSession session;

    protected PictureViewsComputerWorker(String repoName, DocumentRef ref,
            String xpath, int retry) {
        super();
        this.repoName = repoName;
        this.ref = ref;
        this.xpath = xpath;
        this.retry = retry;
    }

    public PictureViewsComputerWorker(String repoName, DocumentRef ref,
            String xpath) {
        this(repoName, ref, xpath, 0);
    }

    @Override
    protected boolean isTransactional() {
        // we manage AdHoc TX
        return false;
    }

    public CoreSession initSessionIfNeeded(String repositoryName)
            throws Exception {
        if (loginContext != null && session != null) {
            return session;
        }
        try {
            if (loginContext == null) {
                loginContext = Framework.login();
            }
        } catch (LoginException e) {
            log.error("Cannot log in", e);
        }
        RepositoryManager repositoryManager = Framework.getLocalService(RepositoryManager.class);
        if (repositoryManager == null) {
            // would happen if only low-level repo is initialized
            throw new RuntimeException(
                    "RepositoryManager service not available");
        }
        Repository repository;
        if (repositoryName != null) {
            repository = repositoryManager.getRepository(repositoryName);
        } else {
            repository = repositoryManager.getDefaultRepository();
            repositoryName = repository.getName();
        }
        session = repository.open();
        return session;
    }

    @Override
    public String getTitle() {
        return "generate picture views for Document " + ref;
    }

    @Override
    public String getCategory() {
        return "PictureConversion";
    }

    @Override
    public void work() throws Exception {

        DocumentModel workingDocument = null;

        try {
            TransactionHelper.startTransaction();
            initSessionIfNeeded(repoName);
            if (session.exists(ref)) {
                DocumentModel pictureDoc = session.getDocument(ref);
                if (!pictureDoc.isImmutable()) {
                    pictureDoc.detach(true);
                    workingDocument = pictureDoc;
                } else {
                    setStatus("Target Document is read only");
                    log.warn("Can not compute view for doc " + ref
                            + " since it is read only");
                }
            } else {
                setStatus("Can not find target Document");
                log.warn("Can not compute view for doc " + ref
                        + " since it no longer exists !");
            }
        } catch (Exception e) {
            TransactionHelper.setTransactionRollbackOnly();
        } finally {
            TransactionHelper.commitOrRollbackTransaction();
            if (session != null) {
                CoreInstance.getInstance().close(session);
                session = null;
            }
        }

        // processing outside of TX
        if (workingDocument != null) {
            setStatus("Running conversion");
            Property fileProp = workingDocument.getProperty(xpath);
            // upload blob and create views
            ArrayList<Map<String, Object>> pictureTemplates = null; // use
                                                                    // default !
            PictureResourceAdapter picture = workingDocument.getAdapter(PictureResourceAdapter.class);
            Blob blob = (Blob) fileProp.getValue();
            String filename = blob == null ? null : blob.getFilename();
            String title = (String) workingDocument.getPropertyValue("dc:title"); // re-set
            try {
                picture.createPicture(blob, filename, title, pictureTemplates);
            } catch (IOException e) {
                throw new ClientException(e.toString(), e);
            }

        } else {
            setStatus("Nothing to process");
            return;
        }

        try {
            setStatus("saving document");
            TransactionHelper.startTransaction();
            initSessionIfNeeded(repoName);
            workingDocument.putContextData(
                    PictureChangedListener.DISABLE_PICTURECHANGE_LISTENER, true);
            session.saveDocument(workingDocument);
            setStatus("Picture views updated ok");
        } catch (Exception e) {
            TransactionHelper.setTransactionRollbackOnly();
        } finally {
            TransactionHelper.commitOrRollbackTransaction();
        }

    }

    @Override
    public void cleanUp(boolean ok, Exception e) {
        super.cleanUp(ok, e);
        try {
            if (session != null) {
                CoreInstance.getInstance().close(session);
                session = null;
            }
        } finally {
            if (loginContext != null) {
                try {
                    loginContext.logout();
                    loginContext = null;
                } catch (LoginException le) {
                    log.error("Error while logging out", le);
                }
            }
        }
    }
}
