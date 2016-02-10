package com.ilimi.taxonomy.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilimi.common.exception.ServerException;
import com.ilimi.taxonomy.enums.ContentErrorCodes;


public class CustomParser  {

	
	
	/**
	 * This method read ECML file 
	 * @param file path for index.ecml
	 * @return 
	 * */
	public static Map<String,String> readECMLFile(String filePath,String saveDir){
		final Map<String,String> mediaId = new HashMap<String , String>();
		//final String saveDir1 = saveDir;
		try {

			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();

			DefaultHandler handler = new DefaultHandler() {

				public void startElement(String uri, String localName,String qName, 
						Attributes attributes) throws SAXException {
					if (qName.equalsIgnoreCase("media")) {
						if (attributes.getValue("id")!=null) {
							if (attributes.getValue("src")!=null) {
								mediaId.put(attributes.getValue("id"),attributes.getValue("src"));
							}
						}
					}
				}

				public void endElement(String uri, String localName,
						String qName) throws SAXException {
					//System.out.println("End Element :" + qName);
				}
			};
			saxParser.parse(filePath, handler);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mediaId;
	}

    public static void readECMLFileDownload(String filePath,String assetFolder,Map<String,String> mediaIdURLMap){
		
			String filePath1 = filePath+File.separator+"index.ecml";
			String assetDir = filePath+File.separator+"assets";
			File file1 = new File(assetDir);
			if (!file1.exists()) {
				file1.mkdir();
			}
	        File xmlFile = new File(filePath1);
	        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	        DocumentBuilder dBuilder;
	        try {
	            dBuilder = dbFactory.newDocumentBuilder();
	            Document doc = dBuilder.parse(xmlFile);
	            doc.getDocumentElement().normalize();
	            //update attribute value
	            updateAttributeValue(doc , assetDir,mediaIdURLMap);
	            //write the updated document to file or console
	            doc.getDocumentElement().normalize();
	            TransformerFactory transformerFactory = TransformerFactory.newInstance();
	            Transformer transformer = transformerFactory.newTransformer();
	            DOMSource source = new DOMSource(doc);
	            StreamResult result = new StreamResult(new File(filePath1));
	            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	            transformer.transform(source, result);
	            System.out.println("XML file updated successfully");
	             
	        } catch (Exception e1) {
	            e1.printStackTrace();
	            throw new ServerException(ContentErrorCodes.ERR_CONTENT_PUBLISH.name(), e1.getMessage());
	        }
	}
	
	private static String fileNameInURL[] = null;
	private static String fileNameWithExtn = null;
	private static void updateAttributeValue(Document doc,String saveDir,Map<String,String> mediaIdURLMap) {
        NodeList medias = doc.getElementsByTagName("media");
        Element media = null;
        for(int i=0; i<medias.getLength();i++){
        	media = (Element) medias.item(i);
        	if (mediaIdURLMap!=null && !mediaIdURLMap.isEmpty()) {
        	    String mediaId = media.getAttribute("id");
        	    if (mediaIdURLMap.containsKey(mediaId)) {
        	        String url = mediaIdURLMap.get(mediaId);
        	        if (StringUtils.isNotBlank(url))
        	            media.setAttribute("src", url);
        	    }
			}else if (mediaIdURLMap==null) {
				 String src = media.getAttribute("src");
                 HttpDownloadUtility.downloadFile(src, saveDir);
                 fileNameInURL =  src.split("/");
         		fileNameWithExtn = fileNameInURL[fileNameInURL.length-1];
                 System.out.println(src);
                 media.setAttribute("src", fileNameWithExtn);
			}
           
          
        }
    }
    /**
     * Read any type of file
     * @author Rajiv
     * @param File type
     * @return text, String Type
     * */
    public static String readFile(File file){
    	String text = "";
    	try (FileInputStream fis = new FileInputStream(file);){ 
    		text = IOUtils.toString(fis, StandardCharsets.UTF_8.name()); 
    	} catch (IOException io) {
    		io.printStackTrace(); 
    	}
    	return text;
    }
    
    
    /**
     * This Method Copy Data and Item Json into ecml as CDATA
     * @author Rajiv
     * @param filePath 
     * @param type : items or data
     * **/
    public static void updateJsonInEcml(String filePath,final String type){
    	try {
    		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    		Document doc = docBuilder.parse(filePath);
    		NodeList attrList = doc.getElementsByTagName("controller");
    		for (int i = 0; i < attrList.getLength(); i++) {
    			//element = (Element) attrList.item(i);
    			Element controller =  (Element) attrList.item(i);
    			if (controller.getAttribute("type").equalsIgnoreCase(type)) {
    				controller =  (Element) attrList.item(i);
    				File file = new File(filePath);
    				String nameOfJsonFile = controller.getAttribute("id");
    				String itemJsonPath = file.getParent()+File.separator+type+File.separator+nameOfJsonFile+".json";
    				File jsonFile = new File(itemJsonPath);
    				if (jsonFile.exists()) {
    					controller.appendChild(doc.createCDATASection(readFile(jsonFile)));
					}
				}
			}
    		doc.getDocumentElement().normalize();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(source, result);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    public static void main(String[] args) {
		//C:\ilimi\StoryFolder\1452487631391_PrathamStories_Day_1_JAN_9_2016\items//C:\\ilimi\\download\\test\\index.ecml", "items
    	updateJsonInEcml("C:\\ilimi\\StoryFolder\\1452487631391_PrathamStories_Day_1_JAN_9_2016\\index.ecml", "items");
    	readJsonFileDownload(null,null);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void readJsonFileDownload(String filePath, String sourceFolder) {
		String assetDir = filePath+File.separator+"assets";
		String jsonFilePath= filePath+File.separator+"index.json";
		String jsonString = readFile(new File(jsonFilePath));
		ObjectMapper mapper = new ObjectMapper();
		List<Map<String, Object>> listOfMedia = new ArrayList<>();
		Map<String, String> idSrcMap = new HashMap<>();
		try {
			Map<String, Object> jsonMap = mapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
			Map<String, Object> jsonThemeMap = (Map<String, Object>) jsonMap.get("theme");
			Map<String, Object> jsonManifestMap = (Map<String, Object>) jsonThemeMap.get("manifest");
			listOfMedia = (List<Map<String, Object>>) jsonManifestMap.get("media");
			Iterator medias =listOfMedia.iterator();
			while (medias.hasNext()) {
				Map<String, Object> media = (Map<String, Object>) medias.next();
				idSrcMap.put((String)media.get("id"), (String)media.get("src"));
			}
			Iterator mediaEntries = idSrcMap.entrySet().iterator();
			while (mediaEntries.hasNext()) {
			    Map.Entry entry = (Map.Entry) mediaEntries.next();
			    String src= (String)entry.getValue();
			    HttpDownloadUtility.downloadFile(src, assetDir);
			    fileNameInURL =  src.split("/");
         		fileNameWithExtn = fileNameInURL[fileNameInURL.length-1];
         		idSrcMap.put((String)entry.getKey(), fileNameWithExtn);
			}
			List<Map<String, Object>> updatedListOfMedia =updateJsonMedia(jsonMap,idSrcMap);
			if (updatedListOfMedia.isEmpty() || updatedListOfMedia!=null) {
				jsonManifestMap.put("media", updatedListOfMedia);
			}
			String updatedJsonString = mapper.writeValueAsString(jsonMap);
			if (StringUtils.isNoneBlank(updatedJsonString)) {
				File file  = new File(jsonFilePath);
				FileUtils.writeStringToFile(file, updatedJsonString);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServerException(ContentErrorCodes.ERR_CONTENT_PUBLISH.name(), e.getMessage());
		}
		
		
		
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static List<Map<String, Object>> updateJsonMedia(Map<String, Object> jsonMap,
			Map<String, String> idSrcMap) {
		List<Map<String, Object>> listOfMedia = new ArrayList<>();
		List<Map<String, Object>> updatedListOfMedia = new ArrayList<>();
		try {
			Map<String, Object> jsonThemeMap = (Map<String, Object>) jsonMap.get("theme");
			Map<String, Object> jsonManifestMap = (Map<String, Object>) jsonThemeMap.get("manifest");
			listOfMedia = (List<Map<String, Object>>) jsonManifestMap.get("media");
			Iterator medias =listOfMedia.iterator();
			while (medias.hasNext()) {
				Map<String, Object> media = (Map<String, Object>) medias.next();
				String id = (String)media.get("id");
				String src = (String)media.get("src");
				if (StringUtils.isNoneBlank(id)) {
					media.put("src", idSrcMap.get(id));
					updatedListOfMedia.add(media);
				}
			}
			return updatedListOfMedia;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
    
    
}
