package com.evolveum.midpoint.web.component.form;

import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.web.page.admin.configuration.component.ObjectSelectionPage;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import org.apache.wicket.Page;
import org.apache.wicket.PageReference;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.feedback.ComponentFeedbackMessageFilter;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.query.InOidFilter;
import com.evolveum.midpoint.prism.query.NotFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.form.multivalue.MultiValueChoosePanel;
import com.evolveum.midpoint.web.component.util.SimplePanel;
import com.evolveum.midpoint.web.page.admin.configuration.component.ObjectSelectionPanel;
import com.evolveum.midpoint.web.page.admin.dto.ObjectViewDto;
import com.evolveum.midpoint.web.page.admin.roles.component.UserOrgReferenceChoosePanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

public class ValueChoosePanel<T, C extends ObjectType> extends SimplePanel<T> {

    private static final Trace LOGGER = TraceManager.getTrace(MultiValueChoosePanel.class);

    private static final String ID_LABEL = "label";

    private static final String ID_TEXT_WRAPPER = "textWrapper";
    private static final String ID_TEXT = "text";
    private static final String ID_FEEDBACK = "feedback";
    private static final String ID_ADD = "add";
    private static final String ID_REMOVE = "remove";
    private static final String ID_BUTTON_GROUP = "buttonGroup";
    private static final String ID_EDIT = "edit";

    protected static final String MODAL_ID_OBJECT_SELECTION_POPUP = "objectSelectionPopup";

    private static final String CLASS_MULTI_VALUE = "multivalue-form";

    public ValueChoosePanel(String id, IModel<T> value, List<PrismReferenceValue> values, boolean required, Class<C> type) {
        super(id, value);
        setOutputMarkupId(true);

        initLayout(value, values, required, type);
    }

    private void initLayout(final IModel<T> value, final List<PrismReferenceValue> values, final boolean required, Class<C> type) {


        WebMarkupContainer textWrapper = new WebMarkupContainer(ID_TEXT_WRAPPER);

        textWrapper.setOutputMarkupId(true);

        TextField text = new TextField<>(ID_TEXT, createTextModel(value));
        text.add(new AjaxFormComponentUpdatingBehavior("onblur") {
            @Override
            protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {
            }
        });
        text.setRequired(required);
        text.setEnabled(false);
        textWrapper.add(text);

        FeedbackPanel feedback = new FeedbackPanel(ID_FEEDBACK, new ComponentFeedbackMessageFilter(text));
        textWrapper.add(feedback);

        AjaxLink edit = new AjaxLink(ID_EDIT) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                editValuePerformed(values, target);
            }
        };
        textWrapper.add(edit);
        add(textWrapper);

        initDialog(type, values);

    }

//	  protected T createNewEmptyItem() throws InstantiationException, IllegalAccessException {
//	        return ty;
//	    }

    protected void replaceIfEmpty(Object object) {
        boolean added = false;
        ObjectReferenceType ort = ObjectTypeUtil.createObjectRef((ObjectType) object);
        ort.setTargetName(((ObjectType) object).getName());
        getModel().setObject((T) ort.asReferenceValue());

    }

    protected void initDialog(final Class<C> type, List<PrismReferenceValue> values) {

        if (FocusType.class.equals(type)) {
            initUserOrgDialog();
        } else {
            initGenericDialog(type, values);


        }
    }

    // for ModalWindow treatment see comments in ChooseTypePanel
    private void initGenericDialog(final Class<C> type, final List<PrismReferenceValue> values) {
        final ModalWindow dialog = new ModalWindow(MODAL_ID_OBJECT_SELECTION_POPUP);

        ObjectSelectionPanel.Context context = new ObjectSelectionPanel.Context(this) {

            // See analogous discussion in ChooseTypePanel
            public ValueChoosePanel getRealParent() {
                return WebMiscUtil.theSameForPage(ValueChoosePanel.this, getCallingPageReference());
            }

            @Override
            public void chooseOperationPerformed(AjaxRequestTarget target, ObjectType object) {
                getRealParent().choosePerformed(target, object);
            }

            @Override
            public ObjectQuery getDataProviderQuery() {
                return getRealParent().createChooseQuery(values);
            }

            @Override
            public boolean isSearchEnabled() {
                return true;
            }

            @Override
            public Class<? extends ObjectType> getObjectTypeClass() {
                return type;
            }

        };

        ObjectSelectionPage.prepareDialog(dialog, context, this, "chooseTypeDialog.title", ID_TEXT_WRAPPER);
        add(dialog);
    }


    private void initUserOrgDialog() {
        final ModalWindow dialog = new ModalWindow(MODAL_ID_OBJECT_SELECTION_POPUP);
        ObjectSelectionPanel.Context context = new ObjectSelectionPanel.Context(this) {

            // See analogous discussion in ChooseTypePanel
            public ValueChoosePanel getRealParent() {
                return WebMiscUtil.theSameForPage(ValueChoosePanel.this, getCallingPageReference());
            }

            @Override
            public void chooseOperationPerformed(AjaxRequestTarget target, ObjectType object) {
                getRealParent().choosePerformed(target, object);
            }

            @Override
            public boolean isSearchEnabled() {
                return true;
            }

            @Override
            public Class<? extends ObjectType> getObjectTypeClass() {
                return UserType.class;
            }

            @Override
            protected WebMarkupContainer createExtraContentContainer(String extraContentId, final ObjectSelectionPanel objectSelectionPanel) {
                return new UserOrgReferenceChoosePanel(extraContentId, Boolean.FALSE) {
                    @Override
                    protected void onReferenceTypeChangePerformed(AjaxRequestTarget target, Boolean newValue) {
                        objectSelectionPanel.updateTableByTypePerformed(target, Boolean.FALSE.equals(newValue) ? UserType.class : OrgType.class);
                    }
                };
            }
        };

        ObjectSelectionPage.prepareDialog(dialog, context, this, "chooseTypeDialog.title", ID_TEXT_WRAPPER);
        add(dialog);
    }

    protected ObjectQuery createChooseQuery(List<PrismReferenceValue> values) {
        ArrayList<String> oidList = new ArrayList<>();
        ObjectQuery query = new ObjectQuery();
//TODO we should add to filter currently displayed value
//not to be displayed on ObjectSelectionPanel instead of saved value
//		for (PrismReferenceValue ref : values) {
//			if (ref != null) {
//				if (ref.getOid() != null && !ref.getOid().isEmpty()) {
//					oidList.add(ref.getOid());
//				}
//			}
//		}

//		if (isediting) {
//			oidList.add(orgModel.getObject().getObject().asObjectable().getOid());
//		}

        if (oidList.isEmpty()) {
            return null;
        }

        ObjectFilter oidFilter = InOidFilter.createInOid(oidList);
        query.setFilter(NotFilter.createNot(oidFilter));

        return query;
    }

    /**
     * @return css class for off-setting other values (not first, left to the
     * first there is a label)
     */
    protected String getOffsetClass() {
        return "col-md-offset-4";
    }

    protected IModel<String> createTextModel(final IModel<T> model) {
        return new AbstractReadOnlyModel<String>() {

            @Override
            public String getObject() {
                T ort = (T) model.getObject();

                if (ort instanceof PrismReferenceValue) {
                    PrismReferenceValue prv = (PrismReferenceValue) ort;
                    return prv == null ? null : (prv.getTargetName() != null ? prv.getTargetName().getOrig() : prv.getOid());
                } else if (ort instanceof ObjectViewDto) {
                    return ((ObjectViewDto) ort).getName();
                }
                return ort.toString();

            }
        };
    }

    protected void editValuePerformed(List<PrismReferenceValue> values, AjaxRequestTarget target) {
        ModalWindow window = (ModalWindow) get(MODAL_ID_OBJECT_SELECTION_POPUP);
        window.show(target);
        ObjectSelectionPanel dialog = (ObjectSelectionPanel) window.get(createComponentPath(window.getContentId(), ObjectSelectionPage.ID_OBJECT_SELECTION_PANEL));
        if (dialog != null) {
            dialog.updateTablePerformed(target, createChooseQuery(values));
        }
    }

    /*
     * TODO - this method contains check, if chosen object already is not in
     * selected values array This is a temporary solution until we well be able
     * to create "already-chosen" query
     */
    protected void choosePerformed(AjaxRequestTarget target, C object) {
        choosePerformedHook(target, object);
        ModalWindow window = (ModalWindow) get(MODAL_ID_OBJECT_SELECTION_POPUP);
        window.close(target);

        if (isObjectUnique(object)) {
            replaceIfEmpty(object);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("New object instance has been added to the model.");
        }
        // has to be done in the context of parent page
        //target.add(this);
    }


    protected boolean isObjectUnique(C object) {

        // for(T o: ){
        PrismReferenceValue old = (PrismReferenceValue) getModelObject();
        if (old == null || old.isEmpty()) {
            return true;
        }
        if (old.getOid().equals(object.getOid())) {
            return false;
        }
        // }
        return true;
    }


    /**
     * A custom code in form of hook that can be run on event of choosing new
     * object with this chooser component
     */
    protected void choosePerformedHook(AjaxRequestTarget target, C object) {
    }

}