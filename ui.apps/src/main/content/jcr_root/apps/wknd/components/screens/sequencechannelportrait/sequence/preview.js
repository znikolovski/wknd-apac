/*
 * ADOBE CONFIDENTIAL
 *
 * Copyright 2015 Adobe Systems Incorporated
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
/* globals request, org, use */
use(function() {
    'use strict';

    var paragraph = this.items.get(0);

    var index = request.requestParameterMap.index;
    if (index) {
        index = parseInt(index[0].string, 10);
        paragraph = this.items.get(index % this.items.size());
    }

    var time = request.requestParameterMap.time;
    if (time) {
        time = parseInt(time[0].string, 10);
        var cacheArray = [];
        var totalDuration = 0;
        var i, d, par, fallback;
        for (i = 0; i < this.items.size(); i++) {
            par = this.items.get(i);
            fallback = fallback || par.resource.parent.adaptTo(org.apache.sling.api.resource.ValueMap).get('duration');
            d = parseInt(par.properties.get('duration', fallback), 10);
            totalDuration += d;
            cacheArray.push({
                duration: totalDuration,
                paragraph: par
            });
            if (time < totalDuration) {
                paragraph = par;
                break;
            }
        }
        if (time >= totalDuration) {
            time = time % totalDuration;
            var item;
            for (i = 0; i < cacheArray.length; i++) {
                item = cacheArray[i];
                if (time < item.duration) {
                    paragraph = item.paragraph;
                    break;
                }
            }
        }
    }

    return {
        paragraph: paragraph
    };

});
