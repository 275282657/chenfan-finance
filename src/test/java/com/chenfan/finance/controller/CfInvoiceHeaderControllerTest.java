package com.chenfan.finance.controller;

import com.alibaba.fastjson.JSON;
import com.chenfan.common.config.Constant;
import com.chenfan.finance.FinanceApplication;
import com.chenfan.finance.model.CfInvoiceHeader;
import com.chenfan.finance.model.CfInvoiceSettlement;
import com.chenfan.finance.model.bo.CfInvoiceHeaderAddBO;
import com.chenfan.finance.model.bo.InvoiceSwitchBanBO;
import com.chenfan.finance.model.dto.InvoiceHeaderGoAddDTO;
import com.chenfan.finance.service.CfInvoiceHeaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * @author Eleven.Xiao
 * @description //junit 单元测试
 * @date 2020/12/9  11:25
 */
@SpringBootTest(classes = FinanceApplication.class)
class CfInvoiceHeaderControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;

    @MockBean
    private CfInvoiceHeaderService invoiceHeaderService;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }


    @Test
    void invoiceHeaderList() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .get("/invoice/list")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }


    @Test
    void goAdd() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .post("/invoice/goAdd")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(JSON.toJSONString(new InvoiceHeaderGoAddDTO()))
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }

    @Test
    void save() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .post("/invoice/save")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(JSON.toJSONString(new CfInvoiceHeaderAddBO()))
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }

    @Test
    void update() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .put("/invoice/update")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(JSON.toJSONString(new CfInvoiceHeaderAddBO()))
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }

    @Test
    void updateInvoiceInfo() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .put("/invoice/updateInvoiceInfo")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(JSON.toJSONString(new CfInvoiceSettlement()))
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }

    @Test
    void updateInvoiceDelayMoney() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .post("/invoice/updateInvoiceDelayMoney")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(JSON.toJSONString(new CfInvoiceHeader()))
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }

    @Test
    void auditInvoices() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .put("/invoice/auditInvoices")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(JSON.toJSONString(new ArrayList<InvoiceSwitchBanBO>()))
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }

    @Test
    void invoiceDetail() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .get("/invoice/invoiceDetail/1")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }

    @Test
    void createNameList() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .get("/invoice/createNameList")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }

    @Test
    void invoiceExport() throws Exception {
        this.mockMvc.perform(
                MockMvcRequestBuilders
                        .get("/invoice/invoiceExport")//TODO
                        .headers(BaseTestConfig.getHttpHeaders())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(MockMvcResultMatchers.status().isOk()).andDo(print()).andReturn();
    }
}