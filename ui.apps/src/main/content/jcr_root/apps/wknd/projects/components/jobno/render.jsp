<%--
  ADOBE CONFIDENTIAL

  Copyright 2015 Adobe Systems Incorporated
  All Rights Reserved.

  NOTICE:  All information contained herein is, and remains
  the property of Adobe Systems Incorporated and its suppliers,
  if any.  The intellectual and technical concepts contained
  herein are proprietary to Adobe Systems Incorporated and its
  suppliers and may be covered by U.S. and Foreign Patents,
  patents in process, and are protected by trade secret or copyright law.
  Dissemination of this information or reproduction of this material
  is strictly forbidden unless prior written permission is obtained
  from Adobe Systems Incorporated.
--%><%
%><%@include file="/libs/granite/ui/global.jsp" %><%
%><%@page session="false"
          import="org.apache.commons.lang.StringUtils,
                  com.adobe.granite.ui.components.AttrBuilder,
                  com.adobe.granite.ui.components.Config,
                  com.adobe.granite.ui.components.Field,
                  com.adobe.granite.ui.components.Tag,javax.jcr.Node" %><%--###
TextField
=========

.. granite:servercomponent:: /libs/granite/ui/components/coral/foundation/form/textfield
   :supertype: /libs/granite/ui/components/coral/foundation/form/field
   
   A text field component.

   It extends :granite:servercomponent:`Field </libs/granite/ui/components/coral/foundation/form/field>` component.

   It has the following content structure:

   .. gnd:gnd::

      [granite:FormTextField] > granite:FormField
      
      /**
       * The name that identifies the field when submitting the form.
       */
      - name (String)
      
      /**
       * The value of the field.
       */
      - value (StringEL)
      
      /**
       * A hint to the user of what can be entered in the field.
       */
      - emptyText (String) i18n
      
      /**
       * Indicates if the field is in disabled state.
       */
      - disabled (Boolean)
      
      /**
       * Indicates if the field is mandatory to be filled.
       */
      - required (Boolean)
      
      /**
       * Indicates if the value can be automatically completed by the browser.
       *
       * See also `MDN documentation regarding autocomplete attribute <https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input>`_.
       */
      - autocomplete (String) = 'off'
      
      /**
       * The ``autofocus`` attribute to lets you specify that the field should have input focus when the page loads,
       * unless the user overrides it, for example by typing in a different control.
       * Only one form element in a document can have the ``autofocus`` attribute.
       */
      - autofocus (Boolean)
      
      /**
       * The name of the validator to be applied. E.g. ``foundation.jcr.name``.
       * See :doc:`validation </jcr_root/libs/granite/ui/components/coral/foundation/clientlibs/foundation/js/validation/index>` in Granite UI.
       */
      - validation (String) multiple
      
      /**
       * The maximum number of characters (in Unicode code points) that the user can enter.
       */
      - maxlength (Long)
###--%><%

    Config cfg = cmp.getConfig();
    ValueMap vm = (ValueMap) request.getAttribute(Field.class.getName());
    Field field = new Field(cfg);

	Resource jobRecord = resourceResolver.getResource("/content/projects");
	ValueMap myProperties = jobRecord.adaptTo(ValueMap.class); // 1
	String jobnumber = myProperties.get("jobnumber", "999");
	int jobno = Integer.parseInt(jobnumber)+1;
	jobnumber = "" + jobno;

	Node resourceNode = resourceResolver.resolve("/content/projects").adaptTo(Node.class);
	resourceNode.setProperty("jobnumber", jobnumber);
	resourceNode.save();



    boolean isMixed = field.isMixed(cmp.getValue());
    
    Tag tag = cmp.consumeTag();
    AttrBuilder attrs = tag.getAttrs();
    cmp.populateCommonAttrs(attrs);

    attrs.add("type", "text");
    attrs.add("name", cfg.get("name", String.class));
    attrs.add("placeholder", i18n.getVar(cfg.get("emptyText", String.class)));
    attrs.addDisabled(cfg.get("disabled", false));
    attrs.add("autocomplete", cfg.get("autocomplete", String.class));
    attrs.addBoolean("autofocus", cfg.get("autofocus", false));

    if (isMixed) {
        attrs.addClass("foundation-field-mixed");
        attrs.add("placeholder", i18n.get("<Mixed Entries>"));
    } else {
        attrs.add("value", jobnumber);
    }

    attrs.add("maxlength", cfg.get("maxlength", Integer.class));

    if (cfg.get("required", false)) {
        attrs.add("aria-required", true);
    }
    
    String validation = StringUtils.join(cfg.get("validation", new String[0]), " ");
    attrs.add("data-foundation-validation", validation);
    attrs.add("data-validation", validation); // Compatibility

    // @coral
    attrs.add("is", "coral-textfield");
    
%><input <%= attrs.build() %>>