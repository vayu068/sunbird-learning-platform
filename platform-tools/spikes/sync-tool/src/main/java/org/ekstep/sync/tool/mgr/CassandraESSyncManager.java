/**
 * @author: Rhea Fernandes
 * @created: 13th May 2019
 */
package org.ekstep.sync.tool.mgr;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.ekstep.common.Platform;
import org.ekstep.common.exception.ClientException;
import org.ekstep.common.exception.ServerException;
import org.ekstep.common.mgr.ConvertGraphNode;
import org.ekstep.common.util.RequestValidatorUtil;
import org.ekstep.graph.dac.model.Node;
import org.ekstep.graph.model.node.DefinitionDTO;
import org.ekstep.learning.hierarchy.store.HierarchyStore;
import org.ekstep.learning.util.ControllerUtil;
import org.ekstep.sync.tool.util.ElasticSearchConnector;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CassandraESSyncManager {

    private ControllerUtil util = new ControllerUtil();
    private ObjectMapper mapper = new ObjectMapper();
    private String graphId;
    private final String objectType = "Content";
    private final String nodeType = "DATA_NODE";

    private int batchSize ;


    private HierarchyStore hierarchyStore = new HierarchyStore();
    private ElasticSearchConnector searchConnector = new ElasticSearchConnector();


    @PostConstruct
    private void init() throws Exception {
        batchSize = Platform.config.hasPath("batch.size") ? Platform.config.getInt("batch.size"): 50;
    }

    public void syncByBookmarkId(String graphId, String resourceId, List<String> bookmarkIds) {
        this.graphId = RequestValidatorUtil.isEmptyOrNull(graphId) ? "domain" : graphId;
        Map<String, Object> hierarchy = getTextbookHierarchy(resourceId);
        if (MapUtils.isNotEmpty(hierarchy)) {
            List<Map<String, Object>> units = getUnitsMetadata(hierarchy, bookmarkIds);
            if(CollectionUtils.isEmpty(bookmarkIds)) {
                Map<String, Object> tbMetaData = getTBMetaData(resourceId);
                if (MapUtils.isNotEmpty(tbMetaData))
                    units.add(tbMetaData);
            }
            if (CollectionUtils.isNotEmpty(units)) {
                List<String> failedUnits = getFailedUnitIds(units, bookmarkIds);
                Map<String, Object> esDocs = getESDocuments(units);
                System.out.println(esDocs.size());
                esDocs.entrySet().forEach(entry -> System.out.println(entry));
                if (MapUtils.isNotEmpty(esDocs))
                    pushToElastic(esDocs);
                if (!CollectionUtils.isEmpty(failedUnits))
                    printMessages("failed", failedUnits, resourceId);
            }
        } else
            System.out.println("Resource is not a Textbook or Textbook is not live");
    }


    public Map<String, Object> getTextbookHierarchy(String resourceId) {
        Map<String, Object> hierarchy;
        if (RequestValidatorUtil.isEmptyOrNull(resourceId))
            throw new ClientException("BLANK_IDENTIFIER", "Identifier is blank.");
        hierarchy = hierarchyStore.getHierarchy(resourceId);
        return hierarchy;
    }


    public List<Map<String, Object>> getUnitsMetadata(Map<String, Object> hierarchy, List<String> bookmarkIds) {
        Boolean flag = false;
        if(CollectionUtils.isEmpty(bookmarkIds)) {
            System.out.println("The whole TextBook will be synced");
            flag = true;
        }
        List<Map<String, Object>> childrenMaps = mapper.convertValue(hierarchy.get("children"), new TypeReference<List<Map<String, Object>>>() {
        });
        List<Map<String, Object>> unitsMetadata = new ArrayList<>();
        getUnitsToBeSynced(unitsMetadata,childrenMaps, bookmarkIds , flag);
        return unitsMetadata;
    }

    private void getUnitsToBeSynced(List<Map<String, Object>> unitsMetadata, List<Map<String, Object>> children, List<String> bookmarkIds, Boolean flag) {
        if (CollectionUtils.isNotEmpty(children)) {
            children.forEach(child -> {
                if (child.containsKey("visibility")
                        && StringUtils.equalsIgnoreCase((String) child.get("visibility"), "parent")) {
                    if (flag)
                        unitsMetadata.add(child);
                    else if (bookmarkIds.contains(child.get("identifier")))
                        unitsMetadata.add(child);
                    if (child.containsKey("children")) {
                        List<Map<String,Object>> newChildren = mapper.convertValue(child.get("children"), new TypeReference<List<Map<String, Object>>>(){});
                        getUnitsToBeSynced(unitsMetadata, newChildren , bookmarkIds, flag);
                    }
                }
            });
        }
    }

    private List<String> getFailedUnitIds(List<Map<String, Object>> units, List<String> bookmarkIds) {
        List<String> failedUnits = new ArrayList<>();
        if(CollectionUtils.isNotEmpty(bookmarkIds)) {
            if (units.size() == bookmarkIds.size())
                return failedUnits;
            units.forEach(unit -> {
                if (bookmarkIds.contains(unit.get("identifier")))
                    bookmarkIds.remove(unit.get("identifier"));
            });
        }
        return bookmarkIds;
    }

    private Map<String,Object> getTBMetaData(String textBookId) {
        Node node = util.getNode(graphId, textBookId);
        if (RequestValidatorUtil.isEmptyOrNull(node))
            throw new ClientException("RESOURCE_NOT_FOUND", "Enter a Valid Textbook id");
        String status = (String) node.getMetadata().get("status");
        if (StringUtils.isNotEmpty(status) && (!StringUtils.equalsIgnoreCase(status,"live")))
            throw new ClientException("RESOURCE_NOT_FOUND", "Text book must be live");
        return ConvertGraphNode.convertGraphNode(node,graphId,util.getDefinition(graphId,objectType),null);
    }

    private Map<String, Object> getESDocuments(List<Map<String, Object>> units) {
        List<String> indexablePropslist;
        Map<String, Object> definition = getDefinition();
        Map<String, Object> esDocument = new HashMap<>();
        List<String> objectTypeList = Platform.config.hasPath("restrict.metadata.objectTypes") ?
                Arrays.asList(Platform.config.getString("restrict.metadata.objectTypes").split(",")) : Collections.emptyList();
        if (objectTypeList.contains(objectType)) {
            indexablePropslist = getIndexableProperties(definition);
            units.forEach(unit -> {
                String identifier = (String) unit.get("identifier");
                if (CollectionUtils.isNotEmpty(indexablePropslist))
                    filterIndexableProps(unit, indexablePropslist);
                putAdditionalFields(unit, identifier);
                esDocument.put( identifier , unit);
            });
        }
        return esDocument;
    }

    private void putAdditionalFields(Map<String, Object> unit, String identifier) {
        unit.put("graph_id", graphId);
        unit.put("identifier", identifier);
        unit.put("objectType", objectType);
        unit.put("nodeType", nodeType);
    }

    private Map<String, Object> getDefinition() {
        DefinitionDTO definition = util.getDefinition(graphId, objectType);
        if (RequestValidatorUtil.isEmptyOrNull(definition)) {
            throw new ServerException("ERR_DEFINITION_NOT_FOUND", "No Definition found for " + objectType);
        }
        return mapper.convertValue(definition, new TypeReference<Map<String, Object>>() {
        });
    }

    //Return a list of all failed units
    public void pushToElastic(Map<String, Object> esDocument) {
        try {
            searchConnector.bulkImport(esDocument);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getLocalizedMessage());
        }
        System.out.println("Syncing data is a success");
    }

    private List<String> getIndexableProperties(Map<String, Object> definition) {
        List<String> propsList = new ArrayList<>();
        List<Map<String, Object>> properties = (List<Map<String, Object>>) definition.get("properties");
        for (Map<String, Object> property : properties) {
            if ((Boolean) property.get("indexed")) {
                propsList.add((String) property.get("propertyName"));
            }
        }
        return propsList;
    }

    private static void filterIndexableProps(Map<String, Object> documentMap, final List<String> indexablePropsList) {
        documentMap.keySet().removeIf(propKey -> !indexablePropsList.contains(propKey));
    }

    private void printMessages(String status, List<String> bookmarkIds, String id) {
        switch (status) {
            case "failed": {
                System.out.println("The units " + bookmarkIds + "of textbook with " + id + " failed. Check if valid unit.");
                break;
            }
            case "success": {
                System.out.println("The units " + bookmarkIds + "of textbook with " + id + " success");
                break;
            }
        }

    }

}