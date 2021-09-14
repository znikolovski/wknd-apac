/*
 * ADOBE CONFIDENTIAL
 *
 * Copyright 2013 Adobe Systems Incorporated
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
(function (window, document, Granite, $) {
    "use strict";

    var ui = $(window).adaptTo("foundation-ui");
    var reviewDialogTitle = Granite.I18n.get("Review Asset(s)");
    var actionOptions = [{
            selector: ".cq-project-admin-actions-changesrequestedasset",
            status: "changesRequested",
            errorMsg: Granite.I18n.get("Failed to request changes on asset"),
            labelText: Granite.I18n.get("Request Changes"),
            dialogTitle: reviewDialogTitle,
            paramSuffix: ""
        }];

    function getCurrentSelectedItems() {
        return $(".cq-damadmin-admin-childpages.foundation-collection .foundation-collection-item.foundation-selections-item");
    }

    function submitStatus(data, options) {
        $.ajax({
            type: "POST",
            url: Granite.HTTP.externalize("/content/dam"),
            data: data,
            success: function () {
                var contentAPI = $(".foundation-content").adaptTo("foundation-content");
                contentAPI.refresh();
                ui.clearWait();
                resetForm();
            },
            error: function () {
                ui.clearWait();
                var msg = options.errorMsg;
                ui.alert(Granite.I18n.get("Error"), msg, "error");
            }
        });
    }

    function submitRetouch(data) {
		var url = "/bin/retouchasset";
        $.ajax({
            type: "POST",
            url: Granite.HTTP.externalize(url),
            data: {"path": data},
            success: function () {
                var contentAPI = $(".foundation-content").adaptTo("foundation-content");
                contentAPI.refresh();
                ui.clearWait();
            },
            error: function () {
                ui.clearWait();
                var msg = Granite.I18n.get("Failed to retouch assets");
                ui.alert(Granite.I18n.get("Error"), msg, "error");
            }
        });
    }

    function buildParameter(assetPaths, options) {
        var k = 0;
        var data = [];
        var basePath = '/content/dam/';
        var path = "";
        var status = options.status;
        var comment = options.comment;
        var suffix = options.paramSuffix;
        data.push({"name": "_charset_", "value": "utf-8"});
        for (var i = 0; i < assetPaths.length; i++) {
            var path = "./" + assetPaths[i].substring(basePath.length) + "/jcr:content/metadata/";
            data.push({"name": path + "dam:status" + suffix, "value": status});
            data.push({"name": path + "dam:statusComment", "value": comment});
        }
        return data;
    }

    function getSelectedAssetPaths() {
        var assetPaths = [];
        var collection = ".foundation-collection .foundation-collection-item.foundation-selections-item";
        $(collection).each(function () {
            var $this = $(this);
            assetPaths.push($(this)[0].dataset.foundationCollectionItemId);
        });
        return assetPaths;
    }

    function resetForm() {
        var $form = $("#reviewAssetDialogForm");
        $form.find('textarea[name="comment"]').val("");
    }

    function showDialog(assetPaths, options) {
        var $dialog = $("#reviewAssetDialog");
        var $form = $("#reviewAssetDialogForm", $dialog);
        var $submitBtn = $("#reviewAssetDialogFormSubmit", $dialog);

        $dialog[0].show();
        $dialog[0].header.innerHTML = options.dialogTitle;
        $submitBtn[0].label.innerHTML = options.labelText;
        $form.find("textarea[name='comment']").val('');

        $submitBtn.off("click").on("click", function (e) {
            options.comment = $form.find('textarea[name="comment"]').val() || "";
            var data = buildParameter(assetPaths, options);
            if (data.length > 0) {
                submitStatus(data, options);
                submitRetouch(assetPaths);
            }
        });
    }

    function getStatuses($selectedItems) {
        var statuses = [];

        if ($selectedItems && $selectedItems.length > 0) {
            statuses = $selectedItems.map(function () {
                return $('.foundation-collection-assets-meta', this)[0].dataset.status;
            }).get();

            // remove duplicates and empty statuses
            statuses = statuses.reduce(function (prev, curr) {
                if (prev.indexOf(curr) === -1) {
                    prev.push(curr);
                }
                return prev;
            }, []);
        }

        return statuses;
    }

    function toggleActionVisibility($selectedItems) {
        var activeStatuses = getStatuses($selectedItems);

        $.each(actionOptions, function (index, option) {
            var status = option.status;
            var selector = option.selector;
            var $action = $(selector);

            if ($selectedItems.length === 0 || activeStatuses.indexOf(status) !== -1) {
                $action.hide();
            } else {
                if (false === _anyItemIsDirectory($selectedItems)) {
                    $action.show();
                } else {
                    $action.hide();
                }
            }
        });

    };

    function _anyItemIsDirectory($selectedItems) {
        var isDirectory = false;

        $selectedItems.each(function (index, item) {
            if ("directory" === $(this).find(".foundation-collection-assets-meta")[0].dataset.foundationCollectionMetaType) {
                isDirectory = true;
                return false;
            }
        });
        return isDirectory;
    }

    $.each(actionOptions, function (index, option) {
        var selector = option.selector;
        $(document).on("click" + selector, selector, function (e) {
            var assetPaths = getSelectedAssetPaths();
            if (assetPaths.length > 0) {
                showDialog(assetPaths, option);
            }
        });
    });

    $(document).on("foundation-selections-change", function (e) {
        var $selectedItems = getCurrentSelectedItems();
        toggleActionVisibility($selectedItems);
    });

})(window, document, Granite, Granite.$);