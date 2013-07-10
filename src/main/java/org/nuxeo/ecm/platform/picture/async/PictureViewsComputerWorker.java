package org.nuxeo.ecm.platform.picture.async;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.runtime.api.Framework;

public class PictureViewsComputerWorker extends AbstractWork {

    protected static final Log log = LogFactory.getLog(PictureViewsComputerWorker.class);

    protected final String repoName;
    protected final DocumentRef ref;
    protected final String xpath;

    protected LoginContext loginContext;

    protected CoreSession session;

    public PictureViewsComputerWorker(String repoName, DocumentRef ref, String xpath) {
        super();
        this.repoName=repoName;
        this.ref=ref;
        this.xpath=xpath;
    }

    public CoreSession initSession(String repositoryName) throws Exception {
        try {
            loginContext = Framework.login();
        } catch (LoginException e) {
            log.error("Cannot log in", e);
        }
        RepositoryManager repositoryManager = Framework.getLocalService(RepositoryManager.class);
        if (repositoryManager == null) {
            // would happen if only low-level repo is initialized
            throw new RuntimeException("RepositoryManager service not available");
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
        initSession(repoName);

        if (session.exists(ref)) {
            DocumentModel pictureDoc = session.getDocument(ref);
            if (!pictureDoc.isImmutable()) {
                Property fileProp = pictureDoc.getProperty(xpath);
                pictureDoc.putContextData(PictureChangedListener.DISABLE_PICTURECHANGE_LISTENER, true);
                BlobHolder bh = pictureDoc.getAdapter(BlobHolder.class);
                bh.setBlob(fileProp.getValue(Blob.class));
                session.saveDocument(pictureDoc);
            } else {
                log.warn("Can not compute view for doc " + ref + " since it is read only");
            }
        } else {
            log.warn("Can not compute view for doc " + ref + " since it no longer exists !");
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
