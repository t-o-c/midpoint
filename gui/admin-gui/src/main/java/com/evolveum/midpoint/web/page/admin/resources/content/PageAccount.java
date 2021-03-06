/*
 * Copyright (c) 2010-2015 Evolveum
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

package com.evolveum.midpoint.web.page.admin.resources.content;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.prism.ContainerStatus;
import com.evolveum.midpoint.web.component.prism.ObjectWrapper;
import com.evolveum.midpoint.web.component.prism.PrismObjectPanel;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.component.util.ObjectWrapperUtil;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.resources.PageAdminResources;
import com.evolveum.midpoint.web.page.admin.resources.PageResources;
import com.evolveum.midpoint.web.resource.img.ImgResources;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.web.util.WebMiscUtil;
import com.evolveum.midpoint.web.util.WebModelUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.util.string.StringValue;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author lazyman
 */
@PageDescriptor(url = "/admin/resources/account", encoder = OnePageParameterEncoder.class, action = {
        @AuthorizationAction(actionUri = PageAdminResources.AUTH_RESOURCE_ALL,
                label = PageAdminResources.AUTH_RESOURCE_ALL_LABEL,
                description = PageAdminResources.AUTH_RESOURCE_ALL_DESCRIPTION),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_RESOURCES_ACCOUNT_URL,
                label = "PageAccount.auth.resourcesAccount.label",
                description = "PageAccount.auth.resourcesAccount.description")})
public class PageAccount extends PageAdminResources {

    private static final Trace LOGGER = TraceManager.getTrace(PageAccount.class);
    private static final String DOT_CLASS = PageAccount.class.getName() + ".";
    private static final String OPERATION_LOAD_ACCOUNT = DOT_CLASS + "loadAccount";
    private static final String OPERATION_SAVE_ACCOUNT = DOT_CLASS + "saveAccount";

    private static final String ID_PROTECTED_MESSAGE = "protectedMessage";

    private IModel<ObjectWrapper<ShadowType>> accountModel;

    public PageAccount() {
        accountModel = new LoadableModel<ObjectWrapper<ShadowType>>(false) {

            @Override
            protected ObjectWrapper<ShadowType> load() {
                return loadAccount();
            }
        };
        initLayout();
    }

    private ObjectWrapper<ShadowType> loadAccount() {
    	Task task = createSimpleTask(OPERATION_LOAD_ACCOUNT);
        OperationResult result = task.getResult();

        Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions.createCollection(
                ShadowType.F_RESOURCE, GetOperationOptions.createResolve());

        StringValue oid = getPageParameters().get(OnePageParameterEncoder.PARAMETER);
        PrismObject<ShadowType> account = WebModelUtils.loadObject(ShadowType.class, oid.toString(), options,
                PageAccount.this, task, result);

        if (account == null) {
            getSession().error(getString("pageAccount.message.cantEditAccount"));
            showResultInSession(result);
            throw new RestartResponseException(PageResources.class);
        }

        ObjectWrapper wrapper = ObjectWrapperUtil.createObjectWrapper(null, null, account, ContainerStatus.MODIFYING, this);
        OperationResultType fetchResult = account.getPropertyRealValue(ShadowType.F_FETCH_RESULT, OperationResultType.class);
        wrapper.setFetchResult(OperationResult.createOperationResult(fetchResult));
        wrapper.setShowEmpty(false);
        return wrapper;
    }

    private void initLayout() {
        Form mainForm = new Form("mainForm");
        mainForm.setMultiPart(true);
        add(mainForm);

        WebMarkupContainer protectedMessage = new WebMarkupContainer(ID_PROTECTED_MESSAGE);
        protectedMessage.add(new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                ObjectWrapper wrapper = accountModel.getObject();
                return wrapper.isProtectedAccount();
            }
        });
        mainForm.add(protectedMessage);

        PrismObjectPanel<ShadowType> userForm = new PrismObjectPanel<ShadowType>("account", accountModel, new PackageResourceReference(
                ImgResources.class, ImgResources.HDD_PRISM), mainForm, this) {

            @Override
            protected IModel<String> createDescription(IModel<ObjectWrapper<ShadowType>> model) {
                return createStringResource("pageAccount.description");
            }
        };
        mainForm.add(userForm);

        initButtons(mainForm);
    }

    private void initButtons(Form mainForm) {
        AjaxSubmitButton save = new AjaxSubmitButton("save", createStringResource("pageAccount.button.save")) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                savePerformed(target);
            }

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                target.add(getFeedbackPanel());
            }
        };
        save.add(new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                ObjectWrapper wrapper = accountModel.getObject();
                return !wrapper.isProtectedAccount();
            }
        });
        mainForm.add(save);

        AjaxButton back = new AjaxButton("back", createStringResource("pageAccount.button.back")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                cancelPerformed(target);
            }
        };
        mainForm.add(back);
    }

    @Override
    protected IModel<String> createPageSubTitleModel() {
        return new LoadableModel<String>(false) {

            @Override
            protected String load() {
                PrismObject<ShadowType> account = accountModel.getObject().getObject();

                ResourceType resource = account.asObjectable().getResource();
                String name = WebMiscUtil.getName(resource);

                return new StringResourceModel("PageAccount.subTitle", PageAccount.this, null, null, name).getString();
            }
        };
    }

    private void savePerformed(AjaxRequestTarget target) {
        LOGGER.debug("Saving account changes.");

        OperationResult result = new OperationResult(OPERATION_SAVE_ACCOUNT);
        try {
            WebMiscUtil.revive(accountModel, getPrismContext());
            ObjectWrapper wrapper = accountModel.getObject();
            ObjectDelta<ShadowType> delta = wrapper.getObjectDelta();
            if (delta == null) {
                return;
            }
            if (delta.getPrismContext() == null) {
                getPrismContext().adopt(delta);
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Account delta computed from form:\n{}", new Object[]{delta.debugDump(3)});
            }

            if (delta.isEmpty()) {
                return;
            }
            WebMiscUtil.encryptCredentials(delta, true, getMidpointApplication());

            Task task = createSimpleTask(OPERATION_SAVE_ACCOUNT);
            Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<ObjectDelta<? extends ObjectType>>();
            deltas.add(delta);

            getModelService().executeChanges(deltas, null, task, result);
            result.recomputeStatus();
        } catch (Exception ex) {
            result.recordFatalError("Couldn't save account.", ex);
            LoggingUtils.logException(LOGGER, "Couldn't save account", ex);
        }

        if (!result.isSuccess()) {
            showResult(result);
            target.add(getFeedbackPanel());
        } else {
            showResultInSession(result);

            returnToAccountList();
        }
    }

    private void cancelPerformed(AjaxRequestTarget target) {
        returnToAccountList();
    }

    private void returnToAccountList() {
        PrismObject<ShadowType> account = accountModel.getObject().getObject();
        ResourceType resource = account.asObjectable().getResource();

        PageParameters parameters = new PageParameters();
        parameters.add(OnePageParameterEncoder.PARAMETER, resource.getOid());
        setResponsePage(PageContentAccounts.class, parameters);
    }
}