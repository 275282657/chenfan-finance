package com.chenfan.finance.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chenfan.common.exception.BusinessException;
import com.chenfan.common.exception.SystemState;
import com.chenfan.common.vo.Response;
import com.chenfan.common.vo.ResponseCode;
import com.chenfan.common.vo.UserVO;
import com.chenfan.finance.commons.aspect.CFRequestHolder;
import com.chenfan.finance.config.BillNoConstantClassField;
import com.chenfan.finance.dao.*;
import com.chenfan.finance.enums.ChargeEnum;
import com.chenfan.finance.enums.InvoiceStatusEnum;
import com.chenfan.finance.enums.NumberEnum;
import com.chenfan.finance.enums.SettlementStatusEnum;
import com.chenfan.finance.model.*;
import com.chenfan.finance.model.bo.CfInvoiceSettlementBO;
import com.chenfan.finance.model.dto.BaseGetBrandInfoList;
import com.chenfan.finance.model.dto.InvoiceSettlementPercentDTO;
import com.chenfan.finance.model.dto.SPoHeader;
import com.chenfan.finance.model.vo.*;
import com.chenfan.finance.server.BaseInfoRemoteServer;
import com.chenfan.finance.server.BaseRemoteServer;
import com.chenfan.finance.server.PrivilegeUserServer;
import com.chenfan.finance.server.VendorCenterServer;
import com.chenfan.finance.service.CfInvoiceHeaderService;
import com.chenfan.finance.service.CfInvoiceSettlementService;
import com.chenfan.finance.utils.BeanUtilCopy;
import com.chenfan.finance.utils.FileUtil;
import com.chenfan.finance.utils.OperateUtil;
import com.chenfan.finance.utils.TimeFormatUtil;
import com.chenfan.finance.utils.pageinfo.PageInfoUtil;
import com.chenfan.privilege.request.SUserVOReq;
import com.chenfan.privilege.response.SUserVORes;
import com.chenfan.vendor.response.VendorResModel;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


/**
 * @author lqliu
 * @Description: ??????
 * @date 2020/10/20 14:40
 */
@Slf4j
@Service
public class CfInvoiceSettlementServiceImpl implements CfInvoiceSettlementService {

    @Resource
    private CfInvoiceSettlementMapper cfInvoiceSettlementMapper;
    @Resource
    private CfInvoiceHeaderMapper invoiceHeaderMapper;
    @Autowired
    private PrivilegeUserServer privilegeUserServer;
    @Resource
    private CfChargeMapper chargeMapper;
    @Autowired
    private BaseRemoteServer baseRemoteServer;
    @Resource
    private CfInvoiceDetailMapper invoiceDetailMapper;
    @Autowired
    private CfInvoiceHeaderService cfInvoiceHeaderService;
    @Autowired
    private VendorCenterServer vendorCenterServer;
    @Autowired
    private CfPoDetailMapper cfPoDetailMapper;
    @Autowired
    private BaseInfoRemoteServer baseInfoRemoteServer;
    @Autowired
    private CfPoHeaderMapper cfPoHeaderMapper;
    @Autowired
    private PageInfoUtil pageInfoUtil;

    /**
     * ???????????????
     *
     * @param cfInvoiceSettlement, userVO]
     * @return int
     * @author llq
     * @date 2020/10/20 16:05
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addSettlement(CfInvoiceSettlement cfInvoiceSettlement, UserVO userVO) {
        CfInvoiceHeader cfInvoiceHeader = invoiceHeaderMapper.selectById(cfInvoiceSettlement.getInvoiceId());
       // getSettlementAmount(cfInvoiceSettlement);
        //?????????????????????????????????????????????
        int state1 = InvoiceStatusEnum.DJS.getCode();
        int state2 = InvoiceStatusEnum.BJS.getCode();
        Assert.isTrue(cfInvoiceHeader.getInvoiceStatus().equals(state1) || cfInvoiceHeader.getInvoiceStatus().equals(state2), "??????????????????????????????????????????????????????");
        int insert;
        CfInvoiceSettlement cfInvoiceSettlementOfGet = new CfInvoiceSettlement();
        cfInvoiceSettlementOfGet.setInvoiceId(cfInvoiceSettlement.getInvoiceId());
        cfInvoiceSettlementOfGet.setInvoiceSettlementRate(cfInvoiceSettlement.getInvoiceSettlementRate());
        Map<String, BigDecimal> settlementAmount = this.getSettlementAmount(cfInvoiceSettlementOfGet);
        BigDecimal settlementMoney=settlementAmount.get("jsje");
        if (cfInvoiceSettlement.getInvoiceSettlementId() == null) {
            //??????????????????
            log.info("add ???????????? :{}", settlementMoney);
            cfInvoiceSettlement.setInvoiceSettlementMoney(settlementMoney);
            String settlementNo = pageInfoUtil.generateBusinessNum(BillNoConstantClassField.SETTLEMENT);
            cfInvoiceSettlement.setInvoiceSettlementNo(settlementNo);
            cfInvoiceSettlement.setInvoiceSettlementStatus(SettlementStatusEnum.FTBS.getCode());
            OperateUtil.onSave(cfInvoiceSettlement, userVO);
            List<CfInvoiceSettlement> cfInvoiceSettlements = cfInvoiceSettlementMapper.selectList(Wrappers.<CfInvoiceSettlement>lambdaQuery().eq(CfInvoiceSettlement::getInvoiceId, cfInvoiceSettlement.getInvoiceId()).notIn(CfInvoiceSettlement::getInvoiceSettlementStatus,0,8));
            BigDecimal sumRate=cfInvoiceSettlements.stream().map(x->x.getInvoiceSettlementRate()).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(BigDecimal.ZERO.compareTo(sumRate)==0){
                //????????????
                invoiceHeaderMapper.associated(cfInvoiceSettlement.getInvoiceNo(),settlementNo);
            }
            if(StringUtils.isBlank(cfInvoiceSettlement.getAccname())){
                cfInvoiceSettlement.setAccname(cfInvoiceHeader.getInvoiceTitle());
            }
            insert = cfInvoiceSettlementMapper.insert(cfInvoiceSettlement);
            cfInvoiceHeaderService.updateInvoiceStatus(cfInvoiceSettlement.getInvoiceId(), userVO,cfInvoiceSettlement);
        } else {
            throw new BusinessException(SystemState.BUSINESS_ERROR.code(),"????????????????????????????????????????????????");

        }
        return insert;
    }

    @Override
    public int updateSettle(CfInvoiceSettlement cfInvoiceSettlement, UserVO userVO) {
        CfInvoiceSettlement update = new CfInvoiceSettlement();
        CfInvoiceSettlement oldCfInvoiceSettlement =cfInvoiceSettlementMapper.selectById(cfInvoiceSettlement.getInvoiceSettlementId());
        if (Objects.isNull(oldCfInvoiceSettlement.getPaymentDate())){
            //?????????????????????,??????????????????????????????
            update.setPaymentDate(new Date());
        }
        update.setInvoiceSettlementId(cfInvoiceSettlement.getInvoiceSettlementId());
        update.setPaymentRemark(cfInvoiceSettlement.getPaymentRemark());
        update.setPaymentMethod(cfInvoiceSettlement.getPaymentMethod());
        update.setAccessory(cfInvoiceSettlement.getAccessory());
        update.setIsInvoice(cfInvoiceSettlement.getIsInvoice());
        update.setAccname(cfInvoiceSettlement.getAccname()==null?"":cfInvoiceSettlement.getAccname());
        update.setVendorLetterHead(cfInvoiceSettlement.getVendorLetterHead());
        update.setBank(cfInvoiceSettlement.getBank());
        update.setBankAccounts(cfInvoiceSettlement.getBankAccounts());
        OperateUtil.onUpdate(update, userVO);
        cfInvoiceHeaderService.updateInvoiceStatus(oldCfInvoiceSettlement.getInvoiceId(),userVO,update);
        return cfInvoiceSettlementMapper.updateById(update);
    }

    @Override
    public Map<String, BigDecimal> getSettlementAmount(CfInvoiceSettlement cfInvoiceSettlement) {
        CfInvoiceHeader cfInvoiceHeader = invoiceHeaderMapper.selectById(cfInvoiceSettlement.getInvoiceId());
        BigDecimal ye;
        Boolean isCheck=false;
        // ??????
        if (ChargeEnum.ARAP_TYPE_AR.getCode().equals(cfInvoiceHeader.getInvoiceType())) {
            ye = cfInvoiceHeader.getInvoicelDebit().subtract(cfInvoiceHeader.getInvoicelCredit()).negate();
        } else {
            ye = cfInvoiceHeader.getInvoicelCredit().subtract(cfInvoiceHeader.getInvoicelDebit());
            isCheck=true;
        }
        log.info("???????????????{}??????????????????{}????????????{}",cfInvoiceHeader.getInvoicelDebit(),cfInvoiceHeader.getInvoicelCredit(),ye);
        ye = ye.subtract(cfInvoiceHeaderService.getPrePayMoneyByInvoiceNo(cfInvoiceHeader.getInvoiceNo(), new StringBuilder()));
        Map<String, BigDecimal> data = new HashMap<>(4);
        //?????????????????????????????????????????????
        int state1 = 2;
        int state2 = 3;
        Assert.isTrue(cfInvoiceHeader.getInvoiceStatus().equals(state1) || cfInvoiceHeader.getInvoiceStatus().equals(state2), "??????????????????????????????????????????????????????");
        List<CfInvoiceSettlement> allSettlements = getAllSettlementByInvoiceId(cfInvoiceSettlement.getInvoiceId());
        BigDecimal all = allSettlements.stream().map(CfInvoiceSettlement::getInvoiceSettlementMoney).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (cfInvoiceSettlement.getInvoiceSettlementId() == null) {
            //??????????????????
            BigDecimal rateSum = getAllRate(cfInvoiceSettlement.getInvoiceId());
            rateSum = rateSum.add(cfInvoiceSettlement.getInvoiceSettlementRate());
            Assert.isTrue(rateSum.compareTo(BigDecimal.ONE) <= 0, "???????????????????????????????????????100%");
            BigDecimal settlementMoney = getSettlementMoney(cfInvoiceSettlement.getInvoiceId(), cfInvoiceSettlement);
            log.info("add ???????????? :{},???????????????{}", settlementMoney,ye);
            if(isCheck){
                if(rateSum.compareTo(BigDecimal.ONE)!=0&&rateSum.compareTo(cfInvoiceSettlement.getInvoiceSettlementRate())==0&&BigDecimal.ZERO.compareTo(settlementMoney)>0){
                    throw new BusinessException(SystemState.BUSINESS_ERROR.code(),"????????????????????????????????????????????????????????????????????????????????????");
                }
            }

            ye = ye.subtract(all).subtract(settlementMoney);
            data.put("zdye", ye);
            data.put("jsje", settlementMoney);
        } else {
            CfInvoiceSettlement db = cfInvoiceSettlementMapper.selectById(cfInvoiceSettlement.getInvoiceSettlementId());
            Assert.isTrue(StringUtils.isBlank(db.getClearNo()), "????????????????????????????????????");
            Assert.isTrue(db.getInvoiceSettlementStatus() != SettlementStatusEnum.SC.getCode(), "??????????????????");
            //??????????????????
            BigDecimal rateSum = getAllRate(cfInvoiceSettlement.getInvoiceId());
            rateSum = rateSum.add(cfInvoiceSettlement.getInvoiceSettlementRate()).subtract(db.getInvoiceSettlementRate());
            Assert.isTrue(rateSum.compareTo(BigDecimal.ONE) <= 0, "???????????????????????????????????????100%");
            BigDecimal settlementMoney = getSettlementMoney(cfInvoiceSettlement.getInvoiceId(), cfInvoiceSettlement);
            log.info("update ???????????? :{}", settlementMoney);
            ye = ye.subtract(all).add(db.getInvoiceSettlementMoney()).subtract(settlementMoney);
            if(isCheck){
                if(rateSum.compareTo(BigDecimal.ONE)!=0&&rateSum.compareTo(cfInvoiceSettlement.getInvoiceSettlementRate())==0&&BigDecimal.ZERO.compareTo(settlementMoney)>0){
                    throw new BusinessException(SystemState.BUSINESS_ERROR.code(),"????????????????????????????????????????????????????????????????????????????????????");
                }
            }
            data.put("zdye", ye);
            data.put("jsje", settlementMoney);
        }
        return data;
    }

    private BigDecimal getAllRate(Long invoiceId) {
        List<CfInvoiceSettlement> selectList = getAllSettlementByInvoiceId(invoiceId);
        return selectList.stream().map(CfInvoiceSettlement::getInvoiceSettlementRate).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<CfInvoiceSettlement> getAllSettlementByInvoiceId(Long invoiceId) {
        LambdaQueryWrapper<CfInvoiceSettlement> lambdaQueryWrapper = Wrappers.lambdaQuery(CfInvoiceSettlement.class);
        lambdaQueryWrapper.eq(CfInvoiceSettlement::getInvoiceId, invoiceId).notIn(CfInvoiceSettlement::getInvoiceSettlementStatus, 0, 8);
        lambdaQueryWrapper.orderByAsc(CfInvoiceSettlement::getCreateDate);
        return cfInvoiceSettlementMapper.selectList(lambdaQueryWrapper);
    }

    @Override
    public void auditSettlement(List<CfInvoiceSettlementBO> modes, UserVO userVO) {
        for (CfInvoiceSettlementBO model : modes) {
            CfInvoiceSettlement cfInvoiceSettlement = cfInvoiceSettlementMapper.selectById(model.getInvoiceSettlementId());
            checkStatus(cfInvoiceSettlement.getInvoiceSettlementStatus(), model.getInvoiceSettlementStatus(), cfInvoiceSettlement, userVO);
            //???????????????????????????????????????????????????????????????????????????????????????????????????
            if(Objects.equals(SettlementStatusEnum.ZF.getCode(),model.getInvoiceSettlementStatus())){
                List<CfInvoiceSettlement> cfInvoiceSettlements = cfInvoiceSettlementMapper.selectList(Wrappers.<CfInvoiceSettlement>lambdaQuery().eq(CfInvoiceSettlement::getInvoiceId, cfInvoiceSettlement.getInvoiceId()).notIn(CfInvoiceSettlement::getInvoiceSettlementStatus, 0, 8).orderByAsc(CfInvoiceSettlement::getCreateDate));
                if(cfInvoiceSettlements.size()>1&&cfInvoiceSettlements.get(NumberEnum.ZERO.getCode()).getInvoiceSettlementId().equals(cfInvoiceSettlement.getInvoiceSettlementId())){
                    throw new BusinessException(SystemState.BUSINESS_ERROR.code(),"?????????????????????????????????002???");
                }
                CfInvoiceHeader cfInvoiceHeader = invoiceHeaderMapper.selectById(cfInvoiceSettlement.getInvoiceId());
                if(StringUtils.isNotBlank(cfInvoiceHeader.getAssociatedInvoiceNo())){
                    throw new BusinessException(SystemState.BUSINESS_ERROR.code(),"???????????????????????????????????????"+cfInvoiceHeader.getAssociatedInvoiceNo()+"??????????????????????????????");
                }
            }
            CfInvoiceSettlement up = new CfInvoiceSettlement();
            up.setInvoiceSettlementId(model.getInvoiceSettlementId());
            up.setInvoiceSettlementStatus(model.getInvoiceSettlementStatus());
            OperateUtil.onUpdate(up, userVO);
            cfInvoiceSettlementMapper.updateById(up);
            if (up.getInvoiceSettlementStatus() == SettlementStatusEnum.ZF.getCode() || up.getInvoiceSettlementStatus() == SettlementStatusEnum.YFK.getCode()) {
                cfInvoiceHeaderService.updateInvoiceStatus(cfInvoiceSettlement.getInvoiceId(), userVO,null);
            }
            if(up.getInvoiceSettlementStatus() == SettlementStatusEnum.ZF.getCode() ){
                invoiceHeaderMapper.unAssociated(null,cfInvoiceSettlement.getInvoiceSettlementNo());
            }
        }
    }

    @Override
    public CfInvoiceHeaderPrintVo paymentReqPrint(Long invoiceSettlementId, UserVO userVo) {
        CfInvoiceSettlement cfInvoiceSettlement = cfInvoiceSettlementMapper.selectById(invoiceSettlementId);
        String res = pageInfoUtil.generateBusinessNum(BillNoConstantClassField.PAY_APPLY_BUSINESS);
        CfInvoiceHeader cfInvoiceHeader = invoiceHeaderMapper.selectById(cfInvoiceSettlement.getInvoiceId());
        CfInvoiceHeaderPrintVo cfInvoiceHeaderPrintVo = new CfInvoiceHeaderPrintVo();
        BeanUtils.copyProperties(cfInvoiceHeader, cfInvoiceHeaderPrintVo);
        Response<BaseGetBrandInfoList> brandInfo = baseInfoRemoteServer.getBrandInfo(cfInvoiceHeader.getBrandId().intValue());
        if (null != brandInfo && null != brandInfo.getObj()) {
            cfInvoiceHeaderPrintVo.setBrandName(brandInfo.getObj().getBrandName());
        }
        BigDecimal paymentMoney = cfInvoiceSettlement.getInvoiceSettlementMoney().setScale(2,BigDecimal.ROUND_HALF_UP);
        cfInvoiceHeaderPrintVo.setInvoiceSettlementId(cfInvoiceSettlement.getInvoiceSettlementId());
        cfInvoiceHeaderPrintVo.setPaymentRemark(cfInvoiceSettlement.getPaymentRemark());
        cfInvoiceHeaderPrintVo.setAccessory(cfInvoiceSettlement.getAccessory());
        cfInvoiceHeaderPrintVo.setPaymentMethod(cfInvoiceSettlement.getPaymentMethod());
        cfInvoiceHeaderPrintVo.setIsInvoice(cfInvoiceSettlement.getIsInvoice());
        cfInvoiceHeaderPrintVo.setPaymentMoney(paymentMoney);
        cfInvoiceHeaderPrintVo.setPaymentType("??????");
        cfInvoiceHeaderPrintVo.setPaymentCode(res);
        //????????????????????? ?????????????????????:?????? ?????????????????????
        cfInvoiceHeaderPrintVo.setPaymentDate(Objects.isNull(cfInvoiceSettlement.getPaymentDate())?new Date():cfInvoiceSettlement.getPaymentDate());
        cfInvoiceHeaderPrintVo.setProposer(userVo.getRealName());
        cfInvoiceHeaderPrintVo.setInvoiceSettlementNo(cfInvoiceSettlement.getInvoiceSettlementNo());
        List<Integer> poTypeList = invoiceHeaderMapper.getPoType(cfInvoiceSettlement.getInvoiceId());
        List<Integer> newList = new ArrayList(new TreeSet(poTypeList));
        StringBuffer poType = new StringBuffer();
        if (newList.size() > 0) {
            newList.forEach(x -> {
                if (x == 0) {
                    poType.append("??????,");
                } else {
                    poType.append("??????,");
                }
            });
            poType.deleteCharAt(poType.length() - 1);
        }
        cfInvoiceHeaderPrintVo.setPoType(poType.toString());
        Response<SUserVORes> serverUserInfo = privilegeUserServer.getUserInfo(SUserVOReq.builder().userId(userVo.getUserId()).build());
        if (Objects.nonNull(serverUserInfo) && serverUserInfo.getCode() == ResponseCode.SUCCESS.getCode()) {
            SUserVORes userVoRes = serverUserInfo.getObj();
            if (Objects.nonNull(userVoRes)) {
                cfInvoiceHeaderPrintVo.setDepartment(userVoRes.getDepartmentName());
                cfInvoiceHeaderPrintVo.setDuties(userVo.getPositionName());
            }
        }
        Response<VendorResModel> vendorByCode = vendorCenterServer.getVendorByCode(null, cfInvoiceHeaderPrintVo.getBalance());
        cfInvoiceHeaderPrintVo.setVendorId(vendorByCode.getObj().getVendorId());
        cfInvoiceHeaderPrintVo.setSalesType(getSaleType(cfInvoiceHeader.getInvoiceNo()));

        Response<VendorResModel> info = vendorCenterServer.getInfo(vendorByCode.getObj().getVendorId());
        cfInvoiceHeaderPrintVo.setVendorLetterHead(cfInvoiceSettlement.getVendorLetterHead() == null ? info.getObj().getVendorLetterhead() : cfInvoiceSettlement.getVendorLetterHead());
        cfInvoiceHeaderPrintVo.setAccname(cfInvoiceSettlement.getAccname() == null ? info.getObj().getAccname() : cfInvoiceSettlement.getAccname());
        cfInvoiceHeaderPrintVo.setBank(cfInvoiceSettlement.getBank() == null ? cfInvoiceHeader.getBank() : cfInvoiceSettlement.getBank());
        cfInvoiceHeaderPrintVo.setBankAccounts(cfInvoiceSettlement.getBankAccounts() == null ? cfInvoiceHeader.getBankAccounts() : cfInvoiceSettlement.getBankAccounts());
        return cfInvoiceHeaderPrintVo;
    }

    private String getSaleType(String invoNo) {
        List<CfCharge> cfCharges = chargeMapper.selectList(Wrappers.lambdaQuery(CfCharge.class)
                .in(CfCharge::getInvoiceNo, invoNo));
        Set<Integer> collect = cfCharges.stream().map(CfCharge::getSalesType).filter(Objects::nonNull).collect(Collectors.toSet());
        StringBuilder bd = new StringBuilder("");
        if (collect.size() < 1) {
            bd.append("???????????????").append(",");
        }
        for (Integer integer : collect) {
            if (integer == 1) {
                bd.append("?????????").append(",");
            } else {
                bd.append("?????????").append(",");
            }
        }
        return bd.substring(0, bd.length() - 1);
    }


    @Override
    public CfInvoiceHeaderDetailVO print(Long invoiceSettlementId) {
        CfInvoiceSettlement cfInvoiceSettlement = cfInvoiceSettlementMapper.selectById(invoiceSettlementId);

        CfInvoiceHeaderDetailVO detail = cfInvoiceHeaderService.detail(cfInvoiceSettlement.getInvoiceId(),false);
        List<CfInvoiceSettlement> cfInvoiceSettlements = cfInvoiceSettlementMapper.selectByAssociated(null, cfInvoiceSettlement.getInvoiceSettlementNo());
        if(org.apache.commons.collections4.CollectionUtils.isNotEmpty(cfInvoiceSettlements)){
            detail.setAssociatedInvoiceNoList(cfInvoiceSettlements.stream().map(x->x.getInvoiceNo()).collect(Collectors.toList()));
            detail.setAssociatedInvoiceSettlementNoList(cfInvoiceSettlements.stream().map(x->x.getInvoiceSettlementNo()).collect(Collectors.toList()));
        }
        ChargeInVO totalChargeIn = detail.getChargeInList().get(detail.getChargeInList().size() - 1);
        //detail.setAdjustQcMoney(detail.getChargeInList().get(detail.getChargeInList().size() - 1).getQaDeductions());
        detail.setAdjustQcMoney(totalChargeIn.getQaDeductions());
        detail.setAdjustDelayMoney(totalChargeIn.getPostponeDeductionsTotal());
        detail.setAdjustOtherMoney(totalChargeIn.getOthersDeductions());
        detail.setAdjustRedMoney(totalChargeIn.getRedDeductions());
        detail.setAdjustTaxMoney(totalChargeIn.getTaxDiff());
        detail.setSettlementStatus(cfInvoiceSettlement.getInvoiceSettlementStatus());
        BigDecimal invoiceSettlementRate = cfInvoiceSettlement.getInvoiceSettlementRate();
        String rate = invoiceSettlementRate.multiply(new BigDecimal("100")).intValue() + "%";
        detail.setSettlementRate(rate);
        // ?????????????????????????????? ??????????????????
        updateChargeInlistData(cfInvoiceSettlement, detail);

        Response<VendorResModel> vendorByCode = vendorCenterServer.getVendorByCode(null, detail.getBalance());
        detail.setVendorName(vendorByCode.getObj().getVendorName());
        detail.setVendorAbbName(vendorByCode.getObj().getVenAbbName());
        List<CfInvoiceSettlementVo> collect = detail.getSettlementList().stream().filter((a) -> a.getInvoiceSettlementId().equals(invoiceSettlementId)).collect(Collectors.toList());
        detail.setSettlementList(collect);
        return detail;
    }

    private void updateChargeInlistData(CfInvoiceSettlement printSet, CfInvoiceHeaderDetailVO detail) {
        Long invoiceId = printSet.getInvoiceId();
        List<ChargeInVO> chargeInListInput = detail.getChargeInList();
        getInvoiceAmount(chargeInListInput);
        log.info("all==============================================");
        sout(chargeInListInput);
        List<ChargeInVO> chargeInListInputNew = JSONArray.parseArray(JSONObject.toJSONString(chargeInListInput), ChargeInVO.class);
        List<CfInvoiceSettlement> allSettlements = getAllSettlementByInvoiceId(invoiceId);
        //  ?????????
        ChargeInVO lastOrFist;
        if (allSettlements.get(0).getInvoiceSettlementId().equals(printSet.getInvoiceSettlementId())) {
            BigDecimal invoiceSettlementRate = printSet.getInvoiceSettlementRate();
            getFirstSettlementAndUpdateChargeIn(invoiceSettlementRate, chargeInListInput,true);
            // ??????????????????????????????
            lastOrFist = setSumSettlement(chargeInListInput);
            sout(chargeInListInput);
        } else {
            // ????????????
            CfInvoiceSettlement db = allSettlements.get(0);
            assert db != null;
            BigDecimal invoiceSettlementRate = db.getInvoiceSettlementRate();
            getFirstSettlementAndUpdateChargeIn(invoiceSettlementRate, chargeInListInput,false);
            // ??????????????????????????????
            setSumSettlement(chargeInListInput);
            BigDecimal lastRate = BigDecimal.ONE.subtract(db.getInvoiceSettlementRate());
            String rate = lastRate.multiply(new BigDecimal("100")).intValue() + "%";
            Assert.isTrue(lastRate.compareTo(printSet.getInvoiceSettlementRate()) == 0, "????????????????????????????????? :" + rate);
            //  ???????????? 2-n
            log.info("first==============================================");
            sout(chargeInListInput);

            // ?????????????????? ?????????-????????? * ??????
            lastOrFist = getLastSettlement(chargeInListInputNew, chargeInListInput);
            log.info(" last ==============================================");
            sout(chargeInListInputNew);
            // ????????????????????????
            // ????????????
            detail.setChargeInList(chargeInListInputNew);
        }
        detail.setAdjustRealMoney(lastOrFist.getSettlementAmount());
    }


    /**
     * ??????????????????????????????
     *
     * @param invoiceId
     * @param inputSettle
     * @return
     */
    public BigDecimal getSettlementMoney(Long invoiceId, CfInvoiceSettlement inputSettle) {
        CfInvoiceHeaderDetailVO detail = cfInvoiceHeaderService.detail(invoiceId,false);

        List<ChargeInVO> chargeInListInput = detail.getChargeInList();
        getInvoiceAmount(chargeInListInput);
        log.info("all==============================================");
        sout(chargeInListInput);
        List<ChargeInVO> chargeInListInputNew = JSONArray.parseArray(JSONObject.toJSONString(chargeInListInput), ChargeInVO.class);
        List<CfInvoiceSettlement> allSettlements = getAllSettlementByInvoiceId(invoiceId);
        // ?????? ?????????
        Boolean check = CollectionUtils.isEmpty(allSettlements)
                // ????????????????????????
                || (allSettlements.size() == 1 && allSettlements.get(0).getInvoiceSettlementId().equals(inputSettle.getInvoiceSettlementId()));
        if (check) {
            BigDecimal invoiceSettlementRate = inputSettle.getInvoiceSettlementRate();
            getFirstSettlementAndUpdateChargeIn(invoiceSettlementRate, chargeInListInput,true);
            // ??????????????????????????????
            setSumSettlement(chargeInListInput);
            sout(chargeInListInput);
            ChargeInVO yjeVo = getHeji(chargeInListInput);
            return yjeVo.getSettlementAmount();
        } else {
            // ????????????
            CfInvoiceSettlement db = allSettlements.get(0);
            assert db != null;
            BigDecimal invoiceSettlementRate = db.getInvoiceSettlementRate();
            getFirstSettlementAndUpdateChargeIn(invoiceSettlementRate, chargeInListInput,false);
            // ??????????????????????????????
            setSumSettlement(chargeInListInput);
            BigDecimal lastRate = BigDecimal.ONE.subtract(db.getInvoiceSettlementRate());
            String rate = lastRate.multiply(new BigDecimal("100")).intValue() + "%";
            Assert.isTrue(lastRate.compareTo(inputSettle.getInvoiceSettlementRate()) == 0, "????????????????????????????????? :" + rate);
            //  ???????????? 2-n
            log.info("first==============================================");
            sout(chargeInListInput);

            // ?????????????????? ?????????-????????? * ??????
            getLastSettlement(chargeInListInputNew, chargeInListInput);
            log.info(" last ==============================================");
            sout(chargeInListInputNew);
            ChargeInVO yjeVo = getHeji(chargeInListInputNew);
            return yjeVo.getSettlementAmount();
        }
    }

    private void getInvoiceAmount(List<ChargeInVO> chargeInListInput) {
        List<ChargeInVO> chargeInList = getNewChargeInvo(chargeInListInput);
        BigDecimal invoAll = BigDecimal.ZERO;
        for (ChargeInVO no : chargeInList) {
            // ???????????????
            int invSum = no.getActualQty() - no.getDefectiveRejectionQty();
            BigDecimal invoiceAmount = new BigDecimal(invSum).multiply(no.getCostsPrice());
            no.setInvoiceAmount(invoiceAmount);
            invoAll = invoAll.add(invoiceAmount);
        }
        ChargeInVO h = getHeji(chargeInListInput);
        ChargeInVO y = getYuanJine(chargeInListInput);
        h.setInvoiceAmount(invoAll);
        y.setInvoiceAmount(invoAll);
    }

    private void sout(List<ChargeInVO> chargeInListInput) {
        for (ChargeInVO chargeInVO : chargeInListInput) {
            StringBuilder builder = new StringBuilder();
            builder.append("| " + getsTr(chargeInVO.getProductCode()));
            builder.append("|  ????????????    |  " + getsTr(chargeInVO.getActualQty()));
            builder.append("|  ????????????    |  " + getsTr(chargeInVO.getDefectiveRejectionQty()));
            builder.append("|  ???????????????  |  " + getsTr(chargeInVO.getInvoiceAmount()));
            builder.append("|  ??????        |  " + getsTr(chargeInVO.getCostsPrice()));
            builder.append("|  ????????????    |  " + getsTr(chargeInVO.getPostponeDeductionsTotal()));
            builder.append("|  ?????????????????? |  " + getsTr(chargeInVO.getActualAmount()));
            builder.append("|  ?????????????????? |  " + getsTr(chargeInVO.getSettlementQty()));
            builder.append("|  ?????????????????? |  " + getsTr(chargeInVO.getSettlementAmount()));
            builder.append("| ");
            log.info(builder.toString());
        }
    }

    private String getsTr(Object d) {
        if (d == null) {
            d = 0;
        }
        String productCode = String.valueOf(d);
        StringBuffer bu = new StringBuffer(productCode);
        if (productCode.length() < NumberEnum.TEN.getCode()) {
            for (int i = 0; i < NumberEnum.TEN.getCode() - productCode.length(); i++) {
                //bu = bu + " ";
                bu.append(" ");
            }
        }
        return bu.toString();
    }


    private ChargeInVO getLastSettlement(List<ChargeInVO> chargeInListInputNew, List<ChargeInVO> chargeInListInput) {
        String yje = "?????????";
        String hj = "??????";
        String tzje = "????????????";
        BigDecimal lastSettlementAmount = BigDecimal.ZERO;
        BigDecimal lastUnitAmount=BigDecimal.ZERO;
        BigDecimal lastTaxMoney=BigDecimal.ZERO;


        for (int i = 0; i < chargeInListInputNew.size(); i++) {
            ChargeInVO all = chargeInListInputNew.get(i);
            ChargeInVO before = chargeInListInput.get(i);
            // ????????????????????????,???????????????

            all.setPostponeDetailArray(new ArrayList<>());
            setOtherFee(all);
            if (yje.equals(all.getProductCode()) || hj.equals(all.getProductCode()) || tzje.equals(all.getProductCode())) {
                if (hj.equals(all.getProductCode()) || yje.equals(all.getProductCode())) {
                    // ?????????????????????????????????
                    int lastQty = all.getSettlementQty() - before.getSettlementQty();
                    all.setSettlementQty(lastQty);

                }
                continue;
            }
            int lastQty = all.getSettlementQty() - before.getSettlementQty();
            BigDecimal lastAmount = all.getCostsPrice().multiply(new BigDecimal(lastQty));



            all.setSettlementQty(lastQty);
            all.setSettlementAmount(lastAmount);
            all.setActualAmount(lastAmount);
            if(Objects.nonNull(all.getTaxRate())&&BigDecimal.ZERO.compareTo(all.getTaxRate())!=0){
                BigDecimal taxRate= all.getTaxRate().divide(new BigDecimal(100),4, BigDecimal.ROUND_HALF_UP);
                all.setTaxMoney(all.getActualAmount().divide(BigDecimal.ONE.add(taxRate),8, BigDecimal.ROUND_HALF_UP).multiply(taxRate));
                all.setUnitAmount(all.getActualAmount().subtract(all.getTaxMoney()));
            }else {
                all.setTaxMoney(BigDecimal.ZERO);
                all.setUnitAmount(all.getActualAmount());
            }
            lastSettlementAmount = lastSettlementAmount.add(lastAmount);
            lastUnitAmount=lastUnitAmount.add(all.getUnitAmount());
            lastTaxMoney=lastTaxMoney.add(all.getTaxMoney());
        }
        // ??????
        ChargeInVO hjC = getChargeInByProduce(chargeInListInputNew, hj);
        hjC.setSettlementAmount(lastSettlementAmount);
        hjC.setActualAmount(lastSettlementAmount);
        hjC.setUnitAmount(lastUnitAmount);
        hjC.setTaxMoney(lastTaxMoney);

        // ?????????
        ChargeInVO yjeC = getChargeInByProduce(chargeInListInputNew, yje);
        yjeC.setSettlementAmount(lastSettlementAmount);
        yjeC.setActualAmount(lastSettlementAmount);
        yjeC.setUnitAmount(lastUnitAmount);
        yjeC.setTaxMoney(lastTaxMoney);
        //????????????
        ChargeInVO tzC = getChargeInByProduce(chargeInListInputNew, tzje);
        tzC.setSettlementAmount(BigDecimal.ZERO);
        tzC.setActualAmount(BigDecimal.ZERO);
        return hjC;
    }

    private void setOtherFee(ChargeInVO d) {
        if (d.getPostponeDeductionsTotal() != null) {
            d.setPostponeDeductionsTotal(BigDecimal.ZERO);
        }
        if (d.getQaDeductions() != null) {
            d.setQaDeductions(BigDecimal.ZERO);
        }
        if (d.getRedDeductions() != null) {
            d.setRedDeductions(BigDecimal.ZERO);
        }
        if (d.getTaxDiff() != null) {
            d.setTaxDiff(BigDecimal.ZERO);
        }
        if (d.getOthersDeductions() != null) {
            d.setOthersDeductions(BigDecimal.ZERO);
        }
        if (d.getAdvancepayAmount() != null) {
            d.setAdvancepayAmount(BigDecimal.ZERO);
        }
        if (d.getAdvancepayAmountActual() != null) {
            d.setAdvancepayAmountActual(BigDecimal.ZERO);
        }
    }

    private ChargeInVO setSumSettlement(List<ChargeInVO> chargeInListInput) {
        List<ChargeInVO> chargeInList = getNewChargeInvo(chargeInListInput);
        BigDecimal settlementAmountSum = BigDecimal.ZERO;
        BigDecimal actAmount = BigDecimal.ZERO;
        BigDecimal unitAmount=BigDecimal.ZERO;
        BigDecimal taxAmount=BigDecimal.ZERO;
        //??????????????? ?????????-?????????
        int settlementAllQty = 0;
        //?????????????????????
        int settlementQtySum = 0;

        int hisNotSettlementQtySum=0;
        for (ChargeInVO chargeInVO : chargeInList) {
            settlementAmountSum = settlementAmountSum.add(chargeInVO.getSettlementAmount());
            settlementQtySum = settlementQtySum + chargeInVO.getSettlementQty();
            // ????????????
            actAmount = actAmount.add(chargeInVO.getActualAmount());
            unitAmount=unitAmount.add(chargeInVO.getUnitAmount());
            taxAmount=taxAmount.add(chargeInVO.getTaxMoney());

            // ???????????? ????????????
            chargeInVO.setSettlementAllQty(chargeInVO.getActualQty() - chargeInVO.getDefectiveRejectionQty());
            settlementAllQty = settlementAllQty + chargeInVO.getSettlementAllQty();
            hisNotSettlementQtySum=hisNotSettlementQtySum+chargeInVO.getHistoryNotSettlementAllQty();

        }

        ChargeInVO h = getHeji(chargeInListInput);
        BigDecimal otherMoneyH = getTotalSumOtherMoney(h, BigDecimal.ONE);
        h.setSettlementAmount(actAmount.subtract(otherMoneyH));
        h.setSettlementQty(settlementQtySum);
        h.setActualAmount(actAmount);
        h.setSettlementAllQty(settlementAllQty);
        h.setUnitAmount(unitAmount);
        h.setTaxMoney(taxAmount);
        h.setHistoryNotSettlementAllQty(hisNotSettlementQtySum);
        ChargeInVO t = getTz(chargeInListInput);
        t.setActualAmount(BigDecimal.ZERO);

        ChargeInVO y = getYuanJine(chargeInListInput);
        y.setSettlementAmount(settlementAmountSum);
        y.setSettlementQty(settlementQtySum);
        y.setActualAmount(actAmount);
        y.setSettlementAllQty(settlementAllQty);
        y.setUnitAmount(unitAmount);
        y.setTaxMoney(taxAmount);
        y.setHistoryNotSettlementAllQty(hisNotSettlementQtySum);
        return h;
    }

    private void getFirstSettlementAndUpdateChargeIn(BigDecimal invoiceSettlementRate, List<ChargeInVO> chargeInList,Boolean isFirst) {
        ChargeInVO chargeInVO = getHeji(chargeInList);
        // ?????????????????????
        // ?????????0.6 ???????????????0.6 ??????????????????  ?????????????????? 60% ???????????????
        BigDecimal actualRateSettlementNew = invoiceSettlementRate;
        log.info("?????????????????????????????? actualRateSettlementNew :{}", actualRateSettlementNew);
        // "?????????????????????????????? ?????? ???????????????????????? ");
        getNewChargeInVo(chargeInList, chargeInVO, actualRateSettlementNew, isFirst);
    }

    public static ChargeInVO getHeji(List<ChargeInVO> chargeInList) {
        String yje = "??????";
        return getChargeInByProduce(chargeInList, yje);
    }

    private ChargeInVO getYuanJine(List<ChargeInVO> chargeInList) {
        String y = "?????????";
        return getChargeInByProduce(chargeInList, y);
    }

    private ChargeInVO getTz(List<ChargeInVO> chargeInList) {
        String t = "????????????";
        return getChargeInByProduce(chargeInList, t);
    }


    private void getNewChargeInVo(List<ChargeInVO> chargeInListInput, ChargeInVO hejiCharge, BigDecimal actualRateSettlementNew,Boolean isFirst) {
        List<ChargeInVO> chargeInList = getNewChargeInvo(chargeInListInput);
        for (int i = 0; i < chargeInList.size(); i++) {
            ChargeInVO no = chargeInList.get(i);

            BigDecimal otherMoney = getTotalSumOtherMoney(no, BigDecimal.ONE);
            // 77
            int invSum = no.getActualQty() - no.getDefectiveRejectionQty();
            // 60%  - >  70%   55
            BigDecimal newInvSum = actualRateSettlementNew.multiply(new BigDecimal(invSum)).setScale(0, BigDecimal.ROUND_HALF_UP);

            // ????????????????????????????????????
            int invRateSum = newInvSum.intValue();
            if(isFirst){
                no.setHistorySettlementAllQty(0);
                no.setHistoryNotSettlementAllQty(invSum-invRateSum);
            }
            // ??????????????????
            no.setSettlementQty(invRateSum);
            // ?????? spu ???????????????
            BigDecimal allMoney = new BigDecimal(invRateSum).multiply(no.getCostsPrice());
            // ???????????????
            no.setActualAmount(allMoney);
            // ????????????
            no.setSettlementAmount(allMoney.subtract(otherMoney));
            if(Objects.nonNull(no.getTaxRate())&&BigDecimal.ZERO.compareTo(no.getTaxRate())!=0){
                BigDecimal taxRate= no.getTaxRate().divide(new BigDecimal(100),4, BigDecimal.ROUND_HALF_UP);
                no.setTaxMoney(no.getActualAmount().divide(BigDecimal.ONE.add(taxRate),8, BigDecimal.ROUND_HALF_UP).multiply(taxRate).setScale(2,BigDecimal.ROUND_HALF_UP));
                no.setUnitAmount(no.getActualAmount().subtract(no.getTaxMoney()));
            }else {
                no.setTaxMoney(BigDecimal.ZERO);
                no.setUnitAmount(no.getActualAmount());
            }
        }
    }

    private List<ChargeInVO> getNewChargeInvo(List<ChargeInVO> chargeInListInput) {
        List<ChargeInVO> charges = new ArrayList<>();
        for (ChargeInVO no : chargeInListInput) {
            if ("?????????".equals(no.getProductCode()) || "????????????".equals(no.getProductCode()) || "??????".equals(no.getProductCode())) {
                continue;
            }
            charges.add(no);
        }
        return charges;
    }

    private List<ChargeInVO> getNewChargeWithOutYuan(List<ChargeInVO> chargeInListInput) {
        List<ChargeInVO> charges = new ArrayList<>();
        for (ChargeInVO no : chargeInListInput) {
            if ("?????????".equals(no.getProductCode())) {
                continue;
            }
            charges.add(no);
        }
        return charges;
    }

    @Override
    public BigDecimal getTotalSumOtherMoney(ChargeInVO chargeInVO, BigDecimal one) {
        BigDecimal otherMoney = BigDecimal.ZERO;
        if (chargeInVO == null) {
            return otherMoney;
        }
        if (chargeInVO.getPostponeDeductionsTotal() != null) {
            otherMoney = otherMoney.add(chargeInVO.getPostponeDeductionsTotal().multiply(one));
        }
        if (chargeInVO.getQaDeductions() != null) {
            otherMoney = otherMoney.add(chargeInVO.getQaDeductions().multiply(one));
        }
        if (chargeInVO.getRedDeductions() != null) {
            otherMoney = otherMoney.add(chargeInVO.getRedDeductions().multiply(one));
        }
        if (chargeInVO.getTaxDiff() != null) {
            otherMoney = otherMoney.add(chargeInVO.getTaxDiff().multiply(one));
        }
        if (chargeInVO.getOthersDeductions() != null) {
            otherMoney = otherMoney.add(chargeInVO.getOthersDeductions().multiply(one));
        }
        if (chargeInVO.getAdvancepayAmountActual() != null) {
            otherMoney = otherMoney.add(chargeInVO.getAdvancepayAmountActual());
        }
        return otherMoney;
    }

    public static ChargeInVO getChargeInByProduce(List<ChargeInVO> chargeInList, String produce) {
        for (ChargeInVO chargeInVO : chargeInList) {
            if (produce.equals(chargeInVO.getProductCode())) {
                return chargeInVO;
            }
        }
        return null;
    }


    /**
     * @param checkStatus ????????????
     * @param status      ??????????????????
     * @param s
     * @param userVO
     */
    public void checkStatus(Integer checkStatus, int status, CfInvoiceSettlement s, UserVO userVO) {
        SettlementStatusEnum in = SettlementStatusEnum.getMsgByCode(checkStatus);
        SettlementStatusEnum up = SettlementStatusEnum.getMsgByCode(status);
        Assert.notNull(in, "??????????????????????????????");
        Assert.notNull(up, "???????????????????????????");
        Assert.isTrue(in != SettlementStatusEnum.SC, "????????????????????????");
        Assert.isTrue(in != up, "?????????????????? " + in.getMsg());

        if (in == SettlementStatusEnum.DKP) {
            doCheckStatus(in, up, getOtherNotInStatus(SettlementStatusEnum.ZF, SettlementStatusEnum.SC));
        } else if (in == SettlementStatusEnum.ZF) {
            doCheckStatus(in, up, getOtherNotInStatus(SettlementStatusEnum.SC));
        } else if (in == SettlementStatusEnum.YHX) {
            Assert.isTrue(StringUtils.isNotBlank(s.getCustomerInvoiceNo()), "??????????????? ????????????????????????");
            doCheckStatus(in, up, SettlementStatusEnum.ZF, SettlementStatusEnum.SC, SettlementStatusEnum.DKP);
        } else if (in == SettlementStatusEnum.FTBS||in == SettlementStatusEnum.YSQFK) {
            doCheckStatus(in, up, getOtherNotInStatus(SettlementStatusEnum.ZF, SettlementStatusEnum.SC, SettlementStatusEnum.DKP));
        } else {
            doCheckStatus(in, up, SettlementStatusEnum.values());
        }
    }

    private SettlementStatusEnum[] getOtherNotInStatus(SettlementStatusEnum... next) {
        SettlementStatusEnum[] values = SettlementStatusEnum.values();
        List<SettlementStatusEnum> notIn = new ArrayList<>();
        for (SettlementStatusEnum value : values) {
            boolean exist = false;
            for (SettlementStatusEnum n : next) {
                if (value == n) {
                    exist = true;
                    break;
                }
            }
            if (!exist) {
                notIn.add(value);
            }
        }
        return notIn.toArray(new SettlementStatusEnum[0]);
    }

    private void doCheckStatus(SettlementStatusEnum in, SettlementStatusEnum update, SettlementStatusEnum... not) {
        String ms = " ????????????????????? ";
        for (SettlementStatusEnum chargeCheckStatusEnum : not) {
            Assert.isTrue(update != chargeCheckStatusEnum, in.getMsg() + ms + chargeCheckStatusEnum.getMsg());
        }
    }


    @Override
    public Response<Object> invoiceDetailSettlementExport(InvoiceSettlementPercentDTO invoiceSettlementPercentDTO, UserVO userVO, HttpServletResponse response) {
        //?????????????????????
        List<InvoiceSettlementExportVO.InvoiceSettlementExport> exportVoList = new ArrayList<>();
        //?????????id
        Long settlementId = invoiceSettlementPercentDTO.getInvoiceSettlementId();
        //????????????
        CfInvoiceSettlement cfInvoiceSettlement = cfInvoiceSettlementMapper.selectById(settlementId);
        CfInvoiceHeaderDetailVO detail = cfInvoiceHeaderService.detail(cfInvoiceSettlement.getInvoiceId(),false);
        detail.setInvoiceNo(cfInvoiceSettlement.getInvoiceSettlementNo());
        detail.setInvoiceStatus(cfInvoiceSettlement.getInvoiceSettlementStatus());
        BigDecimal invoiceSettlementRate = cfInvoiceSettlement.getInvoiceSettlementRate();
        String rate = invoiceSettlementRate.multiply(new BigDecimal("100")).intValue() + "%";
        detail.setSettlementRate(rate);
        // ?????????????????????????????? ??????????????????
        updateChargeInlistData(cfInvoiceSettlement, detail);
        Response<VendorResModel> vendorByCode = vendorCenterServer.getVendorByCode(null, detail.getBalance());
        detail.setVendorName(vendorByCode.getObj().getVendorName());
        detail.setVendorAbbName(vendorByCode.getObj().getVenAbbName());
        detail.setVendorId(vendorByCode.getObj().getVendorId());
        List<CfInvoiceSettlementVo> settlementList = new ArrayList<>(1);
        for (CfInvoiceSettlementVo cfInvoiceSettlementVo : detail.getSettlementList()) {
            if (settlementId.equals(cfInvoiceSettlementVo.getInvoiceSettlementId())) {
                settlementList.add(cfInvoiceSettlementVo);
                detail.setSettlementList(settlementList);
                break;
            }
        }

        //??????????????????????????????????????????????????????
        BigDecimal taxMoney = BigDecimal.ZERO;
        BigDecimal withOutTaxTotal = BigDecimal.ZERO;
        //?????????????????????????????????????????????
        List<ChargeInVO> chargeInList = detail.getChargeInList();
        //?????????productCode????????????????????????
        chargeInList = getNewChargeWithOutYuan(chargeInList);

        List<SPoHeader> hashMaps = cfPoHeaderMapper.selectPoCodeByChargeId(cfInvoiceSettlement.getInvoiceId());
        HashMap<String, List<String>> objectObjectHashMap = new HashMap<>();
        for (SPoHeader bean : hashMaps) {
            if (objectObjectHashMap.containsKey(bean.getProductCode())) {
                objectObjectHashMap.get(bean.getProductCode()).add(bean.getPoCode());
            } else {
                objectObjectHashMap.put(bean.getProductCode(), new ArrayList() {{
                    add(bean.getPoCode());
                }});
            }
        }

    /*    List<CfInvoiceSettlement> allSettlement = cfInvoiceHeaderService.getAllSettlement(cfInvoiceSettlement.getInvoiceId());
        this.truncSettlementAmount(allSettlement,null,cfInvoiceSettlement,null);*/
        for (ChargeInVO in : chargeInList) {
            //TODO ???????????????id??????????????????????????????
            List<String> poCodelist = objectObjectHashMap.get(in.getProductCode());
            StringBuilder poSb = new StringBuilder();
            if (!CollectionUtils.isEmpty(poCodelist)) {
                for (int i = 0; i < poCodelist.size(); i++) {
                    poSb.append(poCodelist.get(i));
                    if (i != poCodelist.size() - 1) {
                        poSb.append("/");
                    }
                }
            }
            InvoiceSettlementExportVO.InvoiceSettlementExport exportVO = new InvoiceSettlementExportVO.InvoiceSettlementExport();
            CfInvoiceSettlementVo cfInvoiceSettlementVo = detail.getSettlementList().get(0);
            exportVO.setPoCode(poSb.toString());
            exportVO.setSettlementNo(cfInvoiceSettlementVo.getInvoiceSettlementNo());
            exportVO.setCreateBy(cfInvoiceSettlementVo.getCreateName());
            exportVO.setCreateTime(TimeFormatUtil.localDateTimeToString(cfInvoiceSettlementVo.getCreateDate()));
            exportVO.setSalesType(in.getSalesType());
            exportVO.setState(cfInvoiceSettlementVo.getInvoiceSettlementStatus());
            exportVO.setBrandName(detail.getBrandName());
            exportVO.setVendorAbbName(detail.getVendorAbbName());
            exportVO.setVendorName(detail.getVendorName());
            exportVO.setSettlementStartDate(TimeFormatUtil.localDateTimeToString(detail.getDateStart()));
            exportVO.setSettlementEndDate(TimeFormatUtil.localDateTimeToString(detail.getDateEnd()));
            exportVO.setProductCode(in.getProductCode());
            exportVO.setProductName(in.getGoodsName());

            //??????????????? ????????????id??????cf_po_detail ?????????
            //?????????????????????
            //????????????????????????????????? *???????????????
            //?????? ???????????????*??????
            BigDecimal unitPrice = in.getUnitPrice();
            BigDecimal markupRate = in.getMarkupRate();
            BigDecimal markupUnitPrice = in.getMarkupUnitPrice();
            BigDecimal taxMoney1 = in.getTaxMoney();
            exportVO.setFreeTaxPrice(unitPrice);
            exportVO.setUpFreeTaxPrice(markupUnitPrice);
            exportVO.setFreeTaxTotalPrice(Objects.nonNull(in.getUnitAmount())?in.getUnitAmount().setScale(2,BigDecimal.ROUND_HALF_UP):BigDecimal.ZERO);
            exportVO.setTaxPrice(taxMoney1);

            if (!Objects.equals(in.getProductCode(), "????????????") && !Objects.equals(in.getProductCode(), "??????")) {
                //??????
                exportVO.setTaxPrice(in.getTaxMoney());
                if(Objects.nonNull(exportVO.getTaxPrice())){
                    exportVO.setTaxPrice(exportVO.getTaxPrice().setScale(2,BigDecimal.ROUND_HALF_UP));
                }
                withOutTaxTotal = withOutTaxTotal.add(Objects.nonNull(in.getUnitAmount())?in.getUnitAmount():BigDecimal.ZERO);
                taxMoney = taxMoney.add(Objects.nonNull(in.getTaxMoney())?in.getTaxMoney():BigDecimal.ZERO);
            }
            exportVO.setArrivalQty(in.getArrivalQty());
            exportVO.setDefectiveRejectionQty(in.getDefectiveRejectionQty());
            exportVO.setRejectionQty(in.getRejectionQty());
            exportVO.setSettlementQty(in.getSettlementQty());
            exportVO.setSettlementAmount(Objects.isNull(in.getActualAmount())?BigDecimal.ZERO:in.getActualAmount().setScale(2,BigDecimal.ROUND_HALF_UP));
            exportVO.setHistoryCount(in.getHistorySettlementAllQty());

            exportVO.setQaDeductions(in.getQaDeductions());
            exportVO.setRedDeductions(in.getRedDeductions());
            exportVO.setTaxDiff(in.getTaxDiff());
            exportVO.setOthersDeductions(in.getOthersDeductions());

            //???????????? cf_po_invoice_detail ??????

            List<Map<String, Integer>> postponeDetailArray = in.getPostponeDetailArray();
            if (!CollectionUtils.isEmpty(postponeDetailArray)) {
                AtomicReference<Integer> totalDelayCount = new AtomicReference<>(0);
                postponeDetailArray.forEach(x -> {
                    x.forEach((k, v) -> {
                        totalDelayCount.set(totalDelayCount.get() + v);
                    });
                });
                exportVO.setDelayCount(totalDelayCount.get());
            }
            exportVO.setPostponeDeductionsTotal(Objects.isNull(in.getPostponeDeductionsTotal())?BigDecimal.ZERO:in.getPostponeDeductionsTotal().setScale(2,BigDecimal.ROUND_HALF_UP));
            exportVO.setAdvancepayAmount(in.getAdvancepayAmount());
            exportVO.setAdvancepayAmountActual(in.getAdvancepayAmountActual());

            exportVO.setRealMoney(Objects.isNull( in.getSettlementAmount())?BigDecimal.ZERO: in.getSettlementAmount().setScale(2,BigDecimal.ROUND_HALF_UP));
            exportVO.setPurchaseInvoiceDate(TimeFormatUtil.localDateTimeToString(cfInvoiceSettlementVo.getCustomerInvoiceDate()));
            exportVO.setPurchaseInvoiceNo(cfInvoiceSettlementVo.getCustomerInvoiceNo());
            exportVO.setAuditBy(detail.getUpdateName());
            exportVO.setRemark(cfInvoiceSettlementVo.getRemark());
            //????????????
            if (Objects.equals(in.getProductCode(), "????????????")) {
                exportVO = new InvoiceSettlementExportVO.InvoiceSettlementExport();
                exportVO.setSettlementNo("????????????");
                exportVO.setQaDeductions(in.getQaDeductions());
                exportVO.setRedDeductions(in.getRedDeductions());
                exportVO.setTaxDiff(in.getTaxDiff());
                exportVO.setOthersDeductions(in.getOthersDeductions());
                exportVO.setPostponeDeductionsTotal(in.getPostponeDeductionsTotal());

                exportVO.setHistoryCount(0);
                exportVO.setSettlementQty(0);
            }
            //??????
            if (Objects.equals(in.getProductCode(), "??????")) {
                exportVO = new InvoiceSettlementExportVO.InvoiceSettlementExport();
                exportVO.setSettlementNo("??????");
                exportVO.setQaDeductions(in.getQaDeductions());
                exportVO.setRedDeductions(in.getRedDeductions());
                exportVO.setTaxDiff(in.getTaxDiff());
                exportVO.setOthersDeductions(in.getOthersDeductions());
                exportVO.setPostponeDeductionsTotal(Objects.isNull(in.getPostponeDeductionsTotal())?BigDecimal.ZERO:in.getPostponeDeductionsTotal().setScale(2,BigDecimal.ROUND_HALF_UP));

                exportVO.setArrivalQty(in.getArrivalQty());
                exportVO.setRejectionQty(in.getRejectionQty());
                exportVO.setDefectiveRejectionQty(in.getDefectiveRejectionQty());

                exportVO.setHistoryCount(in.getHistorySettlementAllQty());
                exportVO.setSettlementQty(in.getSettlementQty());
                exportVO.setSettlementAmount(Objects.isNull(in.getActualAmount())?BigDecimal.ZERO:in.getActualAmount().setScale(2,BigDecimal.ROUND_HALF_UP));
                List<Map<String, Integer>> postponeDetailArrayTotal = in.getPostponeDetailArray();
                if (!CollectionUtils.isEmpty(postponeDetailArrayTotal)) {
                    AtomicReference<Integer> totalDelayCount = new AtomicReference<>(0);
                    postponeDetailArray.forEach(x -> {
                        x.forEach((k, v) -> {
                            totalDelayCount.set(totalDelayCount.get() + v);
                        });
                    });
                    exportVO.setDelayCount(totalDelayCount.get());
                }
                exportVO.setAdvancepayAmount(BigDecimal.ZERO);
                exportVO.setAdvancepayAmountActual(BigDecimal.ZERO);
                exportVO.setRealMoney(in.getSettlementAmount().setScale(2,BigDecimal.ROUND_HALF_UP));
                exportVO.setAdvancepayAmount(in.getAdvancepayAmount());
                exportVO.setAdvancepayAmountActual(in.getAdvancepayAmountActual());
            }

            exportVoList.add(exportVO);
        }
        for (InvoiceSettlementExportVO.InvoiceSettlementExport e : exportVoList) {
            if (Objects.equals("??????", e.getSettlementNo())) {
                e.setFreeTaxTotalPrice(Objects.isNull(withOutTaxTotal)?BigDecimal.ZERO:withOutTaxTotal.setScale(2,BigDecimal.ROUND_HALF_UP));
                e.setTaxPrice(Objects.isNull(taxMoney)?BigDecimal.ZERO:taxMoney.setScale(2,BigDecimal.ROUND_HALF_UP));
            }
        }

        try {
            List exportList=exportVoList;
            Class pojoClass=InvoiceSettlementExportVO.InvoiceSettlementExport.class;
            if(Objects.isNull(invoiceSettlementPercentDTO.getType())||invoiceSettlementPercentDTO.getType()==0){
                pojoClass=InvoiceSettlementExportVO.InvoiceSettlementExportTax.class;
                exportList=BeanUtilCopy.copyListPropertiesIgnoreType(exportVoList, pojoClass);
            }
            FileUtil.exportExcelV2(
                    exportList,
                    "????????????-???????????????",
                    "????????????" + userVO.getRealName(),
                    "????????????-???????????????",
                    pojoClass,
                    StringUtils.format("????????????_?????????:%s??????.xlsx",cfInvoiceSettlement.getInvoiceSettlementNo()),
                    response);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }


    @Override
    public void updateInvoiceAndSettlementStatus(Long settlementId, Long invoiceId, UserVO userVO) {
        //   // ?????????????????????
        CfInvoiceSettlement up = new CfInvoiceSettlement();
        up.setInvoiceSettlementId(settlementId);
        up.setInvoiceSettlementStatus(SettlementStatusEnum.YHX.getCode());
        OperateUtil.onUpdate(up, userVO);
        cfInvoiceSettlementMapper.updateById(up);

        // ?????????????????????????????? ??????=1
        List<CfInvoiceSettlement> settlements = getAllSettlementByInvoiceId(invoiceId);
        BigDecimal allRate = settlements.stream().filter((a) -> a.getInvoiceSettlementStatus().equals(SettlementStatusEnum.YHX.getCode())).map(CfInvoiceSettlement::getInvoiceSettlementRate).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allRate.compareTo(BigDecimal.ONE) == 0) {
            CfInvoiceHeader update = new CfInvoiceHeader();
            update.setInvoiceId(invoiceId);
            update.setCustomerInvoiceStatus(3);
            update.setInvoiceStatus(InvoiceStatusEnum.YHX.getCode());
            OperateUtil.onUpdate(update, userVO);
            invoiceHeaderMapper.updateById(update);
        }
    }


    @Override
    public boolean checkFirstSettlement(Long invoiceId, Long settlementId) {
        List<CfInvoiceSettlement> allSettlements = getAllSettlementByInvoiceId(invoiceId);
        BigDecimal bigDecimal = allSettlements.stream().filter(x -> StringUtils.isNotBlank(x.getClearNo())).map(x -> x.getInvoiceSettlementRate()).reduce(BigDecimal.ZERO, BigDecimal::add);
        //  ?????????
        for (int i = 0; i < allSettlements.size(); i++) {
            if (allSettlements.get(i).getInvoiceSettlementId().equals(settlementId)) {
                CFRequestHolder.setBigDecimalThreadLocal(bigDecimal.add(allSettlements.get(i).getInvoiceSettlementRate()));
                return i==0;
            }
        }
        return false;
    }

    @Override
    public CfInvoiceSettlementInfoVo getInvoiceSettlementList(Long invoiceSettlementId) {
        CfInvoiceSettlement cfInvoiceSettlement = cfInvoiceSettlementMapper.selectById(invoiceSettlementId);
        CfInvoiceHeaderDetailVO detail = cfInvoiceHeaderService.detail(cfInvoiceSettlement.getInvoiceId(),false);
        // ?????????????????????????????? ??????????????????
        updateChargeInlistData(cfInvoiceSettlement, detail);
        CfInvoiceSettlementInfoVo cfInvoiceSettlementInfoVo=new CfInvoiceSettlementInfoVo();
        BeanUtils.copyProperties(cfInvoiceSettlement,cfInvoiceSettlementInfoVo);
        for (ChargeInVO cf: detail.getChargeInList()) {
            OperateUtil.onConvertedDecimal(cf,"actualAmount","settlementAmount","postponeDeductionsTotal","taxMoney","unitAmount");
        }
        cfInvoiceSettlementInfoVo.setChargeInList(detail.getChargeInList());
        return cfInvoiceSettlementInfoVo;
    }

    /**
     * ??????????????????????????????
     *
     * @param cfInvoiceSettlement
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateSettleStatus(CfInvoiceSettlement cfInvoiceSettlement) {
        Assert.notNull(cfInvoiceSettlement.getInvoiceSettlementId(), "????????????id????????????");
        CfInvoiceSettlement oldCfInvoiceSettlement =
                cfInvoiceSettlementMapper.selectById(cfInvoiceSettlement.getInvoiceSettlementId());
        Assert.notNull(oldCfInvoiceSettlement, "????????????????????????");
        //?????????????????????????????????????????????,?????????????????????
        if (SettlementStatusEnum.FTBS.getCode() == oldCfInvoiceSettlement.getInvoiceSettlementStatus()) {
            oldCfInvoiceSettlement.setInvoiceSettlementStatus(SettlementStatusEnum.YSQFK.getCode());
            cfInvoiceSettlementMapper.updateById(oldCfInvoiceSettlement);
        }
    }

}
