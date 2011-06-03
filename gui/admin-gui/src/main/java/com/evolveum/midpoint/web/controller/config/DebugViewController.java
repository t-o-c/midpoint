/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.web.controller.config;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.evolveum.midpoint.api.logging.Trace;
import com.evolveum.midpoint.common.diff.CalculateXmlDiff;
import com.evolveum.midpoint.common.diff.DiffException;
import com.evolveum.midpoint.common.jaxb.JAXBUtil;
import com.evolveum.midpoint.logging.TraceManager;
import com.evolveum.midpoint.validator.ObjectHandler;
import com.evolveum.midpoint.validator.ValidationMessage;
import com.evolveum.midpoint.validator.Validator;
import com.evolveum.midpoint.web.bean.DebugObject;
import com.evolveum.midpoint.web.controller.TemplateController;
import com.evolveum.midpoint.web.controller.util.ControllerUtil;
import com.evolveum.midpoint.web.util.FacesUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectContainerType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectFactory;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.QueryType;
import com.evolveum.midpoint.xml.ns._public.repository.repository_1.FaultMessage;
import com.evolveum.midpoint.xml.ns._public.repository.repository_1.RepositoryPortType;

/**
 * 
 * @author lazyman
 * 
 */
@Controller("debugView")
@Scope("session")
public class DebugViewController implements Serializable {

	public static final String PAGE_NAVIGATION_LIST = "/config/debugList?faces-redirect=true";
	public static final String PAGE_NAVIGATION_VIEW = "/config/debugView?faces-redirect=true";
	private static final long serialVersionUID = -6260309359121248206L;
	private static final Trace TRACE = TraceManager.getTrace(DebugViewController.class);
	@Autowired(required = true)
	private transient TemplateController template;
	@Autowired(required = true)
	private transient RepositoryPortType repositoryService;
	private boolean editOther = false;
	private String editOtherName;
	private DebugObject object;
	private boolean editable = false;
	private String xml;

	public String getEditOtherName() {
		return editOtherName;
	}

	public void setEditOtherName(String editOtherName) {
		this.editOtherName = editOtherName;
	}

	public boolean isEditOther() {
		if (!isViewEditable()) {
			editOther = true;
		}
		return editOther;
	}

	public void setEditOther(boolean editOther) {
		this.editOther = editOther;
	}

	public DebugObject getObject() {
		return object;
	}

	public void setObject(DebugObject object) {
		this.object = object;
	}

	public boolean isEditable() {
		return editable;
	}

	public void setEditable(boolean editable) {
		this.editable = editable;
	}

	public String getXml() {
		return xml;
	}

	public void setXml(String xml) {
		this.xml = xml;
	}

	public boolean isViewEditable() {
		if (StringUtils.isEmpty(xml) || object == null) {
			return false;
		}

		return true;
	}

	public String initController() {
		object = null;
		xml = null;
		editable = false;

		return PAGE_NAVIGATION_VIEW;
	}

	public String back() {
		initController();
		template.setSelectedLeftId("leftList");

		return PAGE_NAVIGATION_LIST;
	}

	public String editOtherObject() {
		if (StringUtils.isEmpty(editOtherName)) {
			FacesUtils.addErrorMessage("Object name must not be null.");
			return null;
		}

		QueryType query = new QueryType();
		query.setFilter(ControllerUtil.createQuery(editOtherName));
		try {
			ObjectListType list = repositoryService.searchObjects(query, new PagingType());
			List<ObjectType> objects = list.getObject();
			if (objects.isEmpty()) {
				FacesUtils.addErrorMessage("Couldn't find object that matches name '" + editOtherName + "'.");
				return null;
			}
			if (objects.size() > 1) {
				FacesUtils.addErrorMessage("Found more than one object that matches name '" + editOtherName
						+ "'.");
				return null;
			}
			ObjectType objectType = objects.get(0);
			object = new DebugObject(objectType.getOid(), objectType.getName());
			xml = JAXBUtil.marshal(new ObjectFactory().createObject(objectType));
		} catch (FaultMessage ex) {
			FacesUtils.addErrorMessage("Couldn't search for object '" + object.getName() + "'.", ex);
			TRACE.debug("Couldn't search for object '" + object.getName() + "'.", ex);
			return PAGE_NAVIGATION_LIST;
		} catch (JAXBException ex) {
			FacesUtils.addErrorMessage("Unknown error occured.", ex);
			TRACE.debug("Unknown error occured.", ex);
			return PAGE_NAVIGATION_LIST;
		}

		return viewObject();
	}

	public String viewObject() {
		if (object == null) {
			FacesUtils.addErrorMessage("Debug object not defined.");
			return PAGE_NAVIGATION_LIST;
		}

		try {
			ObjectContainerType container = repositoryService.getObject(object.getOid(),
					new PropertyReferenceListType());
			ObjectType objectType = container.getObject();
			object = new DebugObject(objectType.getOid(), objectType.getName());

			xml = JAXBUtil.marshal(new ObjectFactory().createObject(objectType));
		} catch (FaultMessage ex) {
			FacesUtils.addErrorMessage(
					"Couldn't get object '" + object.getName() + "' with oid '" + object.getOid() + "'.", ex);
			TRACE.debug("Couldn't get object '" + object.getName() + "' with oid '" + object.getOid() + "'.",
					ex);
			return PAGE_NAVIGATION_LIST;
		} catch (JAXBException ex) {
			FacesUtils.addErrorMessage("Couldn't show object '" + object.getName() + "' in editor.", ex);
			TRACE.debug("Couldn't show object '" + object.getName() + "' in editor.", ex);
			return PAGE_NAVIGATION_LIST;
		} catch (Exception ex) {
			FacesUtils.addErrorMessage("Unknown error occured.", ex);
			TRACE.debug("Unknown error occured.", ex);
			return PAGE_NAVIGATION_LIST;
		}

		return PAGE_NAVIGATION_VIEW;
	}

	public String savePerformed() {
		if (StringUtils.isEmpty(xml)) {
			FacesUtils.addErrorMessage("Xml editor is empty.");
			return null;
		}

		ObjectType newObject = getObjectFromXml(xml);
		if (newObject == null) {
			return null;
		}

		try {
			ObjectContainerType container = repositoryService.getObject(object.getOid(),
					new PropertyReferenceListType());
			ObjectType oldObject = container.getObject();
			if (oldObject == null) {
				FacesUtils.addErrorMessage("Object " + object.getName() + "' doesn't exist.");
				return PAGE_NAVIGATION_LIST;
			}

			ObjectModificationType objectChange = CalculateXmlDiff.calculateChanges(oldObject, newObject);
			repositoryService.modifyObject(objectChange);
		} catch (FaultMessage ex) {
			FacesUtils.addErrorMessage("Couln't update object '" + object.getName() + "'.", ex);
			// TODO: logging
			
			return null;
		} catch (DiffException ex) {
			FacesUtils.addErrorMessage("Couln't create diff for object '" + object.getName() + "'.", ex);
			// TODO: logging
			
			return null;
		}

		template.setSelectedLeftId("leftList");

		return PAGE_NAVIGATION_LIST;
	}

	private ObjectType getObjectFromXml(String xml) {
		final List<ObjectType> objects = new ArrayList<ObjectType>();
		Validator validator = new Validator(new ObjectHandler() {

			@Override
			public void handleObject(ObjectType object, List<ValidationMessage> objectErrors) {
				if (objects.isEmpty()) {
					objects.add(object);
				}
			}
		});
		try {
			List<ValidationMessage> messages = validator.validate(IOUtils.toInputStream(xml, "utf-8"));
			if (messages != null && !messages.isEmpty()) {
				StringBuilder builder;
				for (ValidationMessage message : messages) {
					builder = new StringBuilder();
					builder.append(message.getType());
					builder.append(": Object with oid '");
					builder.append(message.getOid());
					builder.append("' is not valid, reason: ");
					builder.append(message.getMessage());
					builder.append(".");
					if (!StringUtils.isEmpty(message.getProperty())) {
						builder.append(" Property: ");
						builder.append(message.getProperty());
					}
					FacesUtils.addErrorMessage(builder.toString());
				}
				return null;
			}
		} catch (IOException ex) {
			FacesUtils.addErrorMessage("Couldn't create object from xml.", ex);
			// TODO: logging
			return null;
		}

		if (objects.isEmpty()) {
			FacesUtils.addErrorMessage("Couldn't create object from xml.");
			return null;
		}

		return objects.get(0);
	}
}
