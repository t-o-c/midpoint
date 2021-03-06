/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.component.prism;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

/**
 * @author lazyman
 */
public class PropertyWrapper<I extends Item> implements ItemWrapper, Serializable, DebugDumpable {

    private ContainerWrapper container;
    private I property;
    private ValueStatus status;
    private List<ValueWrapper> values;
    private String displayName;
    private boolean readonly;
    private ItemDefinition itemDefinition;

    public PropertyWrapper(ContainerWrapper container, I property, boolean readonly, ValueStatus status) {
        Validate.notNull(property, "Property must not be null.");
        Validate.notNull(status, "Property status must not be null.");

        this.container = container;
        this.property = property;
        this.status = status;
        this.readonly = readonly;
        this.itemDefinition = getItemDefinition();

        ItemPath passwordPath = new ItemPath(SchemaConstantsGenerated.C_CREDENTIALS,
                CredentialsType.F_PASSWORD);
        if (passwordPath.equivalent(container.getPath())
                && PasswordType.F_VALUE.equals(property.getElementName())) {
            displayName = "prismPropertyPanel.name.credentials.password";
        }
        
        values = createValues();
    }

    public void revive(PrismContext prismContext) throws SchemaException {
        if (property != null) {
            property.revive(prismContext);
        }
        if (itemDefinition != null) {
            itemDefinition.revive(prismContext);
        }
    }

    @Override
    public ItemDefinition getItemDefinition() {
    	ItemDefinition propDef = null;
    	if (container.getItemDefinition() != null){
    		propDef = container.getItemDefinition().findItemDefinition(property.getDefinition().getName());
    	}
    	if (propDef == null) {
    		propDef = property.getDefinition();
    	}
    	return propDef;
    }
    
    public boolean isVisible() {
        if (property.getDefinition().isOperational()) {
            return false;
        }

        return container.isItemVisible(this);
    }
    
    

    public ContainerWrapper getContainer() {
        return container;
    }

    @Override
    public String getDisplayName() {
        if (StringUtils.isNotEmpty(displayName)) {
            return displayName;
        }
        return ContainerWrapper.getDisplayNameFromItem(property);
    }
    
    @Override
	public QName getName() {
		return getItem().getElementName();
	}

	@Override
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ValueStatus getStatus() {
        return status;
    }

    public void setStatus(ValueStatus status) {
        this.status = status;
    }

    public List<ValueWrapper> getValues() {
        return values;
    }

    @Override
    public I getItem() {
        return property;
    }
    
    public ItemDefinition getDefinition() {
    	return property.getDefinition();
    }

    private List<ValueWrapper> createValues() {
        List<ValueWrapper> values = new ArrayList<ValueWrapper>();

        for (PrismValue prismValue : (List<PrismValue>) property.getValues()) {
            values.add(new ValueWrapper(this, prismValue, ValueStatus.NOT_CHANGED));
        }

        int minOccurs = property.getDefinition().getMinOccurs();
        while (values.size() < minOccurs) {
            values.add(createAddedValue());
        }

        if (values.isEmpty()) {
            values.add(createAddedValue());
        }

        return values;
    }

    public void addValue(){
        getValues().add(createAddedValue());
    }

    public ValueWrapper createAddedValue() {
        ItemDefinition definition = property.getDefinition();

        ValueWrapper wrapper;
        if (SchemaConstants.T_POLY_STRING_TYPE.equals(definition.getTypeName())) {
            wrapper = new ValueWrapper(this, new PrismPropertyValue(new PolyString("")),
                    new PrismPropertyValue(new PolyString("")), ValueStatus.ADDED);
        } else if (isUser() && isThisPropertyActivationEnabled()) {
            wrapper = new ValueWrapper(this, new PrismPropertyValue(null),
                    new PrismPropertyValue(null), ValueStatus.ADDED);
        } else {
            wrapper = new ValueWrapper(this, new PrismPropertyValue(null), ValueStatus.ADDED);
        }

        return wrapper;
    }

    private boolean isUser() {
        ObjectWrapper wrapper = getContainer().getObject();
        PrismObject object = wrapper.getObject();

        return UserType.class.isAssignableFrom(object.getCompileTimeClass());
    }

    private boolean isThisPropertyActivationEnabled() {
        if (!new ItemPath(UserType.F_ACTIVATION).equivalent(container.getPath())) {
            return false;
        }

        if (!ActivationType.F_ADMINISTRATIVE_STATUS.equals(property.getElementName())) {
            return false;
        }

        if (ContainerStatus.MODIFYING.equals(container.getObject().getStatus())) {
            //when modifying then we don't want to create "true" value for c:activation/c:enabled, only during add
            return false;
        }

        return true;
    }

    public boolean hasChanged() {
        for (ValueWrapper value : getValues()) {
            switch (value.getStatus()) {
                case DELETED:
                    return true;
                case ADDED:
                case NOT_CHANGED:
                    if (value.hasValueChanged()) {
                        return true;
                    }
            }
        }

        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PropertyWrapper(");
        builder.append(getDisplayName());
        builder.append(" (");
        builder.append(status);
        builder.append(") ");
        builder.append(getValues() == null ? null :  getValues().size());
		builder.append(" values)");
        builder.append(")");
        return builder.toString();
    }

    @Override
    public boolean isReadonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }
    
    @Override
	public boolean isEmpty() {
		return getItem().isEmpty();
	}

	@Override
	public String debugDump() {
		return debugDump(0);
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.indentDebugDump(sb, indent);
		sb.append(getDebugName());
		sb.append(": ").append(PrettyPrinter.prettyPrint(getName())).append("\n");
		DebugUtil.debugDumpWithLabel(sb, "displayName", displayName, indent+1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "status", status == null?null:status.toString(), indent+1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "readonly", readonly, indent+1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "itemDefinition", itemDefinition == null?null:itemDefinition.toString(), indent+1);
		sb.append("\n");
		DebugUtil.debugDumpWithLabel(sb, "property", property == null?null:property.toString(), indent+1);
		sb.append("\n");
		DebugUtil.debugDumpLabel(sb, "values", indent+1);
		sb.append("\n");
		DebugUtil.debugDump(sb, values, indent+2, false);
		return sb.toString();
	}

	protected String getDebugName() {
		return "PropertyWrapper";
	}

}
