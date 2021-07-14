package com.adobe.aem.guides.wknd.core.models;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.ExporterOption;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Required;
import org.apache.sling.models.annotations.Source;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.fit.cssbox.css.CSSNorm;
import org.fit.cssbox.css.DOMAnalyzer;
import org.fit.cssbox.css.NormalOutput;
import org.fit.cssbox.css.Output;
import org.fit.cssbox.io.DOMSource;
import org.fit.cssbox.io.DefaultDOMSource;
import org.fit.cssbox.io.DocumentSource;
import javax.json.JsonException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.adobe.aem.guides.wknd.core.services.EnvironmentTypeProvider;
import com.day.cq.commons.Externalizer;
import com.day.cq.contentsync.handler.util.RequestResponseFactory;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMMode;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Model(
	// This must adapt from a SlingHttpServletRequest, since this is invoked directly via a request, and not via a resource.
	// If can specify Resource.class as a second adaptable as needed
	adaptables = { SlingHttpServletRequest.class },
	// The resourceType is required if you want Sling to "naturally" expose this model as the exporter for a Resource.
	resourceType = "wknd/components/xf-edm",
	defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
// name = the registered name of the exporter
// extensions = the extensions this exporter is registered to
// selector = defaults to "model", can override as needed; This is helpful if a single resource needs 2 different JSON renditions
@Exporter(name = "jackson", extensions = "json", options = {
	/**
	 * Jackson options:
	 * - Mapper Features: http://static.javadoc.io/com.fasterxml.jackson.core/jackson-databind/2.8.5/com/fasterxml/jackson/databind/MapperFeature.html
	 * - Serialization Features: http://static.javadoc.io/com.fasterxml.jackson.core/jackson-databind/2.8.5/com/fasterxml/jackson/databind/SerializationFeature.html
	 */
	@ExporterOption(name = "MapperFeature.SORT_PROPERTIES_ALPHABETICALLY", value = "true"),
	@ExporterOption(name = "MapperFeature.AUTO_DETECT_GETTERS", value = "true"),
	@ExporterOption(name = "SerializationFeature.WRITE_DATES_AS_TIMESTAMPS", value="false")
})
public class InlineExperienceFragmentModel {

	@Self
	protected SlingHttpServletRequest request;

	@Inject
	protected Resource resource;

	protected Externalizer externalizer;

	@Inject @Source("sling-object") @Required
	private ResourceResolver resourceResolver;

	@Inject
    @Source("osgi-services")
	private RequestResponseFactory requestResponseFactory;

	/** Service to process requests through Sling */
	@Inject
    @Source("osgi-services")
	private SlingRequestProcessor requestProcessor;
	
	@Inject
    @Source("osgi-services")
	EnvironmentTypeProvider environmentTypeProvider;

	private String xfHtml;
	private String innerHtml;

	// Internal state populated via @PostConstruct logic
	private Page page;
	
	private String mode;
	
	private static final Logger LOG = LoggerFactory.getLogger(InlineExperienceFragmentModel.class);
	private static final String RAW_MODE = "raw";
	private static final String EDM_MODE = "edm";

	@PostConstruct
	// PostConstructs are called after all the injection has occurred, but before the Model object is returned for use.
	private void init() throws JsonException, IOException {
		page = resourceResolver.adaptTo(PageManager.class).getContainingPage(resource);
		
		String[] selectors = request.getRequestPathInfo().getSelectors();
		if(ArrayUtils.contains(selectors, RAW_MODE)) {

			mode = RAW_MODE;
		} else {
			mode = EDM_MODE;
		}

		externalizer = resourceResolver.adaptTo(Externalizer.class);
		
		DocumentSource docSource = null;
		
		if(environmentTypeProvider.getEnvironmentType().equals(Externalizer.AUTHOR)) {
			docSource = new AEMDocumentSource(new URL(externalizer.authorLink(resourceResolver, page.getPath()) + ".html?wcmmode=disabled"));
		} else {
			docSource = new AEMDocumentSource(new URL(externalizer.publishLink(resourceResolver, page.getPath()) + ".html"));
		}

		//Parse the input document
		DOMSource parser = new DefaultDOMSource(docSource);
		try {
			Document doc = parser.parse();

			//Create the CSS analyzer
			DOMAnalyzer da = new DOMAnalyzer(doc, docSource.getURL());
			da.attributesToStyles(); //convert the HTML presentation attributes to inline styles
			da.addStyleSheet(null, CSSNorm.stdStyleSheet(), DOMAnalyzer.Origin.AGENT); //use the standard style sheet
			da.addStyleSheet(null, CSSNorm.userStyleSheet(), DOMAnalyzer.Origin.AGENT); //use the additional style sheet
			da.getStyleSheets(); //load the author style sheets

			//Compute the styles
			LOG.info("Computing style...");
			da.stylesToDomInherited();

			//Save the output
			OutputStream os = new ByteArrayOutputStream();
			Output out = new NormalOutput(doc);
			out.dumpTo(os);
			xfHtml = os.toString();
			
			org.jsoup.nodes.Document jSoupDoc = Jsoup.parse(xfHtml);
			Elements select = jSoupDoc.select("a");
		    for (Element e : select){
		    	String href = e.attr("href");
		    	if(environmentTypeProvider.getEnvironmentType().equals(Externalizer.AUTHOR)) {
		    		href = externalizer.authorLink(resourceResolver, href);
				} else {
					href = externalizer.publishLink(resourceResolver, href);
				}
		    	
		        e.attr("href", href);
		    }

		    //now we process the imgs
		    select = jSoupDoc.select("img");
		    for (Element e : select){
		    	String src = e.attr("src");
		    	if(environmentTypeProvider.getEnvironmentType().equals(Externalizer.AUTHOR)) {
		    		src = externalizer.authorLink(resourceResolver, src);
				} else {
					src = externalizer.publishLink(resourceResolver, src);
				}
		    	
		        e.attr("src", src);
		    }
		    jSoupDoc.select("script,.hidden").remove();
		    
		    xfHtml = jSoupDoc.html();
			
			innerHtml = jSoupDoc.body().html();
			
			os.close();
			docSource.close();
		} catch (SAXException e) {
			LOG.error("Error parsing the HTML document", e);
		}
	}

	public String getXFHtml() {
		return xfHtml;
	}
	
	public String getInlineHtml() {
		return innerHtml;
	}
	
	@JsonIgnore
	public String getMode() {
		return mode;
	}

	private class AEMDocumentSource extends DocumentSource {
		private InputStream is;
		private URL url;

		public AEMDocumentSource(URL url) throws IOException {
			super(url);
			this.url = url;
		}

		@Override
		public String getContentType() {
			return "text/html";
		}

		@Override
		public InputStream getInputStream() throws IOException {
			is = getPageContentAsHtml();
			return is;
		}

		@Override
		public void close() throws IOException {
			if(is != null) {
				is.close();
			}
		}

		@Override
		public URL getURL() {
			return url;
		}

		private InputStream getPageContentAsHtml() {
			Map<String, Object> config = new HashMap<String, Object>();
			config.put(ResourceResolverFactory.SUBSERVICE, "wefinanceService");

			try {

				/* Setup request */
				HttpServletRequest req = requestResponseFactory.createRequest("GET", getURL().getPath());
				WCMMode.DISABLED.toRequest(req);

				/* Setup response */
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				HttpServletResponse resp = requestResponseFactory.createResponse(out);

				/* Process request through Sling */
				requestProcessor.processRequest(req, resp, resourceResolver);
				return new ByteArrayInputStream(out.toByteArray());
			} catch (ServletException e) {
				LOG.error("Error making a Sling Servlet call", e);
			} catch (IOException e) {
				LOG.error("IO Error", e);
			}

			return null;
		}

	}

}
