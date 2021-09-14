package com.adobe.aem.guides.wknd.core.main;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aarora
 */
@Component(service = PreconfigurationService.class, immediate = true)
public class PreconfigurationService {

    private static final Logger log = LoggerFactory.getLogger(PreconfigurationService.class);

    private static String CREDS = "hiYzVTUV";
    
    private static String SVCREDS = "Sv$adm1n";
    
    public static final String REP_WRITE = "rep:write";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Activate
    public void activate() throws LoginException, RepositoryException {
        log.info("****activate code running automatically on bundle activation****");

        ResourceResolver resolver = null;

        try {
            resolver = resolverFactory.getServiceResourceResolver(null);
            log.info(resolver.getUserID());
            //addAdminUser(resolver.adaptTo(Session.class));

            Session jcrsession = resolver.adaptTo(Session.class);

            // Create Groups
            createGroup(jcrsession, "projects-request_assets_for_new_wknd_adventure");
            createGroup(jcrsession, "projects-request_assets_for_new_wknd_adventure-owner");
            createGroup(jcrsession, "projects-request_assets_for_new_wknd_adventure-editor");

            // Create Users and assign groups
            createUser(jcrsession, "marketer", CREDS, new String[]{"dam-users", "projects-users"});
            addProfilePic(jcrsession, "marketer");

            createUser(jcrsession, "assetsupplier", CREDS, new String[]{"dam-users", "projects-users", "projects-request_assets_for_new_wknd_adventure", "projects-request_assets_for_new_wknd_adventure-editor"});
            addProfilePic(jcrsession, "assetsupplier");

            createUser(jcrsession, "retoucher", CREDS, new String[]{"dam-users", "projects-users", "projects-request_assets_for_new_wknd_adventure", "projects-request_assets_for_new_wknd_adventure-editor"});
            addProfilePic(jcrsession, "retoucher");

            createUser(jcrsession, "admin", CREDS, new String[]{"projects-request_assets_for_new_wknd_adventure", "projects-request_assets_for_new_wknd_adventure-owner"});
            
            createUser(jcrsession, "svadmin", SVCREDS, new String[]{"administrators"});

            // Save Session
            jcrsession.save();
            
            // Create ACLs
            addACLPermissions(jcrsession, true, "marketer", "/content/dam/wknd-assets/legal", false, new String[]{Privilege.JCR_ALL}, null);
            addACLPermissions(jcrsession, true, "marketer", "/content/dam/wknd-assets/adventures/surf-camp-in-costa-rica", false, new String[]{Privilege.JCR_ALL}, null);
            addACLPermissions(jcrsession, true, "marketer", "/content/dam/wknd-assets/adventures/surf-camp-in-costa-rica", true, new String[]{Privilege.JCR_READ}, null);
            
            ValueFactory vf = jcrsession.getValueFactory();
            HashMap<String, Value> restrictionsMap = new HashMap<>();
            restrictionsMap.put("rep:glob", vf.createValue("/*"));
            addACLPermissions(jcrsession, true, "assetsupplier", "/content/dam", false, new String[]{Privilege.JCR_ALL}, restrictionsMap);
            addACLPermissions(jcrsession, true, "assetsupplier", "/content/dam/projects", true, new String[]{Privilege.JCR_ALL}, null);

            addACLPermissions(jcrsession, false, "projects-request_assets_for_new_wknd_adventure", "/content/dam/projects/request-assets-for-new-wknd-adventure", true, new String[]{Privilege.JCR_READ, Privilege.JCR_READ_ACCESS_CONTROL}, null);

            addACLPermissions(jcrsession, false, "projects-request_assets_for_new_wknd_adventure-owner", "/content/dam/projects/request-assets-for-new-wknd-adventure", true, new String[]{Privilege.JCR_ALL}, null);

            addACLPermissions(jcrsession, false, "projects-request_assets_for_new_wknd_adventure-editor", "/content/dam/projects/request-assets-for-new-wknd-adventure", true, new String[]{REP_WRITE, Privilege.JCR_VERSION_MANAGEMENT}, null);
            
            // Save Session
            jcrsession.save();

        } catch (LoginException e) {
            log.error("LoginException", e);
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }
    }

    private static void createGroup(Session session, String groupId) throws RepositoryException {
        log.info("Creating group: " + groupId);
        if (!(session instanceof JackrabbitSession)) {
            throw new RepositoryException("The repository does not support creating group");
        }
        JackrabbitSession jackrabbitSession = (JackrabbitSession) session;
        UserManager userManager = jackrabbitSession.getUserManager();
        Authorizable group = userManager.getAuthorizable(groupId);
        if (group == null) {
            log.info("creating new group");
            group = userManager.createGroup(groupId);
        } else {
            log.info("group already exists");
        }
        log.info("");
    }

    private static void createUser(Session session, String userId, String password, String[] groupIDs) throws RepositoryException {
        log.info("Creating user: " + userId);
        if (!(session instanceof JackrabbitSession)) {
            throw new RepositoryException("The repository does not support creating user");
        }
        JackrabbitSession jackrabbitSession = (JackrabbitSession) session;
        UserManager userManager = jackrabbitSession.getUserManager();
        Authorizable user = userManager.getAuthorizable(userId);
        if (user == null) {
            log.info("creating new user");
            user = userManager.createUser(userId, password);
        } else {
            log.info("user already exists");
        }

        // Adding groups to user
        for (String groupID : groupIDs) {
            log.info("Adding group: " + groupID);
            Authorizable group = userManager.getAuthorizable(groupID);
            if (group != null && group.isGroup()) {
                ((Group) group).addMember(user);
            } else {
                log.warn("Could not find [ {} ] user group to add user [ {} ]", groupID, userId);
            }
        }
        
        log.info("");
    }

    private static void addProfilePic(Session session, String userId) { // TODO
        log.info("Adding profile pic for user: " + userId);
        log.info("");
    }

    public static void addACLPermissions(Session session, boolean isUser, String id, String path, boolean isAllow, String[] privileges, Map<String, Value> restrictions) throws RepositoryException {
        log.info("Adding policy for " + id + " to path " + path);

        // check if path exists
        if (session.nodeExists(path) == false) {
            log.info("Error: path doesn't exist");
            return; // do nothing
        }

        // Get ACL controls
        AccessControlManager accessControlManager = session.getAccessControlManager();

        JackrabbitAccessControlList jackrabbitAccessControlList = AccessControlUtils.getAccessControlList(session, path);

        // Get principal
        if (!(session instanceof JackrabbitSession)) {
            throw new RepositoryException("The repository does not support adding ACL");
        }
        JackrabbitSession jackrabbitSession = (JackrabbitSession) session;
        PrincipalManager principalManager = jackrabbitSession.getPrincipalManager();
        Principal principal = principalManager.getPrincipal(id);

        // Convert Privileges from String
        Privilege[] p = new Privilege[privileges.length];
        for (int i = 0; i < privileges.length; i++) {
            p[i] = accessControlManager.privilegeFromName(privileges[i]);
        }

        // Add permissions
        jackrabbitAccessControlList.addEntry(principal, p, isAllow, restrictions);

        // Save the permissions to code
        accessControlManager.setPolicy(path, jackrabbitAccessControlList);
        log.info("Configured acl permissions for :" + id);
    }

}
