/*
 * ADOBE CONFIDENTIAL
 *
 * Copyright 2017 Adobe Systems Incorporated
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 */
/* globals use, resolver, resource, com, org, java */
use(function() {
    'use strict';

    var shouldRender = true;
    var isTransition = false;
    var pageManager = resource.resourceResolver.adaptTo(com.day.cq.wcm.api.PageManager);
    var channelPage = pageManager.getContainingPage(resource);
    var projectPage = channelPage;

    // Search for the project ancestor, ignoring any corrupted page or non-project pages
    while (projectPage && (!projectPage.getContentResource() || !projectPage.getContentResource().isResourceType(com.adobe.cq.screens.binding.ScreensConstants.RT_PROJECT))) {
        projectPage = projectPage.getParent();
    }

    var projectDefaultDuration = projectPage && projectPage.getContentResource()
        ? projectPage.getContentResource().adaptTo(org.apache.sling.api.resource.ValueMap).get('duration', java.lang.Integer)
        : null;

    var res = this.paragraph;
    if (res) {
        shouldRender = !resolver.isResourceType(res, 'wcm/msm/components/ghost');
        isTransition = resolver.isResourceType(res, 'screens/core/components/content/transition');
    }

    return {
        shouldRender: shouldRender,
        isTransition: isTransition,
        projectDefaultDuration: projectDefaultDuration
    };

});
