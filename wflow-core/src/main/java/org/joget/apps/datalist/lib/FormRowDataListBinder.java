package org.joget.apps.datalist.lib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinderDefault;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.model.DataListInboxBinder;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.dao.FormDataDaoImpl;
import org.joget.apps.form.lib.PasswordField;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormContainer;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.Userview;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.StringUtil;

public class FormRowDataListBinder extends DataListBinderDefault implements DataListInboxBinder {

    private Form cachedForm = null;
    private String cachedTableName = null;
    private String cachedFormDefId = null;
    
    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getName() {
        return "Form Data Binder";
    }

    @Override
    public String getVersion() {
        return "5.0.0";
    }

    @Override
    public String getDescription() {
        return "Retrieves data rows from a form table.";
    }

    @Override
    public String getLabel() {
        return "Form Data Binder";
    }

    @Override
    public String getPropertyOptions() {
        String formDefField = null;
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef != null) {
            String formJsonUrl = "[CONTEXT_PATH]/web/json/console/app/" + appDef.getId() + "/" + appDef.getVersion() + "/formsWithCustomTable/options";
            formDefField = "{name:'formDefId',label:'@@datalist.formrowdatalistbinder.formId@@',type:'selectbox',options_ajax:'" + formJsonUrl + "'}";
        } else {
            formDefField = "{name:'formDefId',label:'@@datalist.formrowdatalistbinder.formId@@',type:'textfield'}";
        }
        Object[] arguments = new Object[]{formDefField};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/datalist/formRowDataListBinder.json", arguments, true, "message/datalist/formRowDataListBinder");
        return json;
    }

    @Override
    public DataListColumn[] getColumns() {
        List<DataListColumn> columns = new ArrayList<DataListColumn>();
        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            
        String tableName = null;
        Form form = null;
        
        // retrieve columns
        form = getSelectedForm();
        if (form != null) {
            tableName = formDataDao.getFormTableName(form);
        } else {
            tableName = getPropertyString("formDefId");
        }
        
        if (tableName != null) {
            Collection<String> columnNames = formDataDao.getFormDefinitionColumnNames(tableName);
            FormData formData = new FormData();
            for (String columnName : columnNames) {
                if (form != null) {
                    Element element = FormUtil.findElement(columnName, form, formData, true);
                    if (element != null && !(element instanceof FormContainer)) {
                        if (!(element instanceof PasswordField)) {
                            String id = element.getPropertyString(FormUtil.PROPERTY_ID);
                            String label = element.getPropertyString(FormUtil.PROPERTY_LABEL);
                            if (id != null && !id.isEmpty()) {
                                if (label == null || label.isEmpty()) {
                                    label = id;
                                }
                                columns.add(new DataListColumn(id, label, true));
                            }
                        }
                    } else {
                        columns.add(new DataListColumn(columnName, columnName, true));
                    }
                } else {
                    columns.add(new DataListColumn(columnName, columnName, true));
                }
            }
        }

        // add default metadata fields
        columns.add(0, new DataListColumn(FormUtil.PROPERTY_MODIFIED_BY, ResourceBundleUtil.getMessage("datalist.formrowdatalistbinder.modifiedBy"), true));
        columns.add(0, new DataListColumn(FormUtil.PROPERTY_CREATED_BY, ResourceBundleUtil.getMessage("datalist.formrowdatalistbinder.createdBy"), true));
        columns.add(0, new DataListColumn(FormUtil.PROPERTY_MODIFIED_BY_NAME, ResourceBundleUtil.getMessage("datalist.formrowdatalistbinder.modifiedByName"), true));
        columns.add(0, new DataListColumn(FormUtil.PROPERTY_CREATED_BY_NAME, ResourceBundleUtil.getMessage("datalist.formrowdatalistbinder.createdByName"), true));
        columns.add(0, new DataListColumn(FormUtil.PROPERTY_DATE_MODIFIED, ResourceBundleUtil.getMessage("datalist.formrowdatalistbinder.dateModified"), true));
        columns.add(0, new DataListColumn(FormUtil.PROPERTY_DATE_CREATED, ResourceBundleUtil.getMessage("datalist.formrowdatalistbinder.dateCreated"), true));
        columns.add(0, new DataListColumn(FormUtil.PROPERTY_ID, ResourceBundleUtil.getMessage("datalist.formrowdatalistbinder.id"), true));

        return columns.toArray(new DataListColumn[0]);
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return "id";
    }

    @Override
    public DataListCollection getData(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects, String sort, Boolean desc, Integer start, Integer rows) {
        DataListService.alterOracleSession();
        DataListCollection resultList = new DataListCollection();

        String formDefId = getPropertyString("formDefId");
        String tableName = getTableName(formDefId);
        if (tableName != null) {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            DataListFilterQueryObject criteria = getCriteria(properties, filterQueryObjects);

            if (isInbox()) {
                //only formDataDao.findCustomQuery support assignment entities when passing it as join table
                List<Map<String, Object>> rowSet = formDataDao.findCustomQuery(formDefId, tableName, null, null, new String[]{FormDataDaoImpl.WORKFLOW_ASSIGNMENT}, criteria.getQuery(), criteria.getValues(), null, null, null, getSortColumnName(sort), desc, start, rows);
                resultList.addAll(rowSet);
            } else {
                FormRowSet rowSet = formDataDao.find(formDefId, tableName, criteria.getQuery(), criteria.getValues(), sort, desc, start, rows);
                resultList.addAll(rowSet);
            }
        }

        return resultList;
    }

    @Override
    public int getDataTotalRowCount(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects) {
        DataListService.alterOracleSession();
        int count = 0;
        
        String formDefId = getPropertyString("formDefId");
        String tableName = getTableName(formDefId);
        if (tableName != null) {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            DataListFilterQueryObject criteria = getCriteria(properties, filterQueryObjects);

            Long rowCount;
            if (isInbox()) {
                //only formDataDao.countCustomQuery support assignment entities when passing it as join table
                rowCount = formDataDao.countCustomQuery(formDefId, tableName, new String[]{FormDataDaoImpl.WORKFLOW_ASSIGNMENT}, criteria.getQuery(), criteria.getValues(), null, null, null);
            } else {
                rowCount = formDataDao.count(formDefId, tableName, criteria.getQuery(), criteria.getValues());
            }
            count = rowCount.intValue();
        }
        return count;
    }

    protected Form getSelectedForm() {
        Form form = null;
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        String formDefId = getPropertyString("formDefId");
        if (formDefId != null) {
            if (cachedForm == null || !formDefId.equals(cachedFormDefId)) {
                AppDefinition appDef = AppUtil.getCurrentAppDefinition();
                FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
                if (formDef != null) {
                    String formJson = formDef.getJson();
                    
                    if (formJson != null) {
                        form = (Form) formService.createElementFromJson(formJson, false);
                        cachedFormDefId = formDefId;
                        cachedForm = form;
                    }
                }
            } else {
                form = cachedForm;
            }
        }
        return form;
    }

    protected String getTableName(String formDefId) {
        String tableName = cachedTableName;
        if (tableName == null) {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            if (appDef != null && formDefId != null) {
                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                tableName = appService.getFormTableName(appDef, formDefId);
                if (tableName == null) {
                    tableName = formDefId;
                }
                cachedTableName = tableName;
            }
        }
        return tableName;
    }

    @Override
    public String getColumnName(String name) {
        if (name != null && !name.isEmpty() && !FormUtil.PROPERTY_ID.equals(name)) {
            String formDefId = getPropertyString("formDefId");
            String tableName = getTableName(formDefId);
            if (tableName != null) {
                FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
                Collection<String> columnNames = formDataDao.getFormDefinitionColumnNames(tableName);
                if (columnNames.contains(name)) {
                    name = FormUtil.PROPERTY_CUSTOM_PROPERTIES + "." + name;
                } else if (FormUtil.PROPERTY_DATE_CREATED.equals(name) || FormUtil.PROPERTY_DATE_MODIFIED.equals(name)) {
                    name = "cast(" + name + " as string)";
                }
            }
        }
        return name;
    }
    
    public String getSortColumnName(String name) {
        if (FormUtil.PROPERTY_DATE_CREATED.equals(name) || FormUtil.PROPERTY_DATE_MODIFIED.equals(name)) {
            return name;
        } else {
            return getColumnName(name);
        }
    }

    protected DataListFilterQueryObject getCriteria(Map properties, DataListFilterQueryObject[] filterQueryObjects) {
        Collection<String> params = new ArrayList<String>();
        String condition = "";
        
        if (isInbox()) { //adding in the where clause for inbox
            filterQueryObjects = getInboxFilterObjects(filterQueryObjects);
        }

        DataListFilterQueryObject filter = processFilterQueryObjects(filterQueryObjects);

        if (filter.getQuery() != null && !filter.getQuery().isEmpty()) {
            condition = " WHERE " + filter.getQuery();
            if (filter.getValues() != null && filter.getValues().length > 0) {
                params.addAll(Arrays.asList(filter.getValues()));
            }
        }

        String extraCondition = (properties.get("extraCondition") != null) ? properties.get("extraCondition").toString() : null;
        String keyName = null;
        if (properties.get(Userview.USERVIEW_KEY_NAME) != null) {
            keyName = properties.get(Userview.USERVIEW_KEY_NAME).toString();
        }
        String keyValue = null;
        if (properties.get(Userview.USERVIEW_KEY_VALUE) != null) {
            keyValue = properties.get(Userview.USERVIEW_KEY_VALUE).toString();
        }

        if (extraCondition != null && extraCondition.contains(USERVIEW_KEY_SYNTAX)) {
            if (keyValue == null) {
                keyValue = "";
            }
            extraCondition = extraCondition.replaceAll(USERVIEW_KEY_SYNTAX, StringUtil.escapeRegex(keyValue));
        } else if (keyName != null && !keyName.isEmpty() && keyValue != null && !keyValue.isEmpty()) {
            if (condition.trim().length() > 0) {
                condition += " AND ";
            } else {
                condition += " WHERE ";
            }
            condition += getColumnName(keyName) + " = ?";
            params.add(keyValue);
        }

        if (extraCondition != null && !extraCondition.isEmpty()) {
            if (condition.trim().length() > 0) {
                condition += " AND ";
            } else {
                condition += " WHERE ";
            }
            condition += "(" + extraCondition + ") ";
        }

        DataListFilterQueryObject queryObject = new DataListFilterQueryObject();
        queryObject.setQuery(condition);
        if (params.size() > 0) {
            queryObject.setValues((String[]) params.toArray(new String[0]));
        }
        return queryObject;
    }

    @Override
    public DataListCollection getInboxData(DataList datalist, Map properties, DataListFilterQueryObject[] filterQueryObjects, String sort, Boolean desc, Integer start, Integer rows) {
        return getData(datalist, properties, filterQueryObjects, sort, desc, start, rows);
    }

    @Override
    public int getInboxDataTotalRowCount(DataList datalist, Map properties, DataListFilterQueryObject[] filterQueryObjects) {
        return getDataTotalRowCount(datalist, properties, filterQueryObjects);
    }
    
    /**
     * Generate where clause based on inbox setting
     * @param filterQueryObjects
     * @return 
     */
    protected DataListFilterQueryObject[] getInboxFilterObjects(DataListFilterQueryObject[] filterQueryObjects) {
        if (inboxSetting != null) {
            List<DataListFilterQueryObject> temp;
            if (filterQueryObjects != null && filterQueryObjects.length > 0) {
                temp = new ArrayList<DataListFilterQueryObject>(Arrays.asList(filterQueryObjects));
            } else {
                temp = new ArrayList<DataListFilterQueryObject>();
            }
            
            DataListFilterQueryObject queryObj = buildInboxCondition();
            if (queryObj != null && queryObj.getQuery() != null && !queryObj.getQuery().isEmpty()) {
                queryObj.setQuery(getColumnName(getPrimaryKeyColumnName()) + " IN (SELECT COALESCE(ass.link.originProcessId, ass.ActivityProcessId) FROM FormDataAssignment ass LEFT JOIN ass.link WHERE 1=1 " + queryObj.getQuery() + ")");
                temp.add(queryObj);
            }
            
            return temp.toArray(new DataListFilterQueryObject[0]);
        } else {
            return filterQueryObjects;
        }
    }
}
