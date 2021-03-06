package com.chenfan.finance.service.helper;

import com.alibaba.fastjson.JSONObject;
import com.chenfan.common.config.BillNoConstantClassField;
import com.chenfan.common.exception.BusinessException;
import com.chenfan.common.exception.SystemState;
import com.chenfan.common.vo.Response;
import com.chenfan.common.vo.ResponseCode;
import com.chenfan.common.vo.UserVO;
import com.chenfan.finance.commons.purchaseexceptions.PurchaseException;
import com.chenfan.finance.commons.utils.BeanMapper;
import com.chenfan.finance.commons.utils.NumberToStringForChineseMoney;
import com.chenfan.finance.dao.AdvancepayApplyMapper;
import com.chenfan.finance.dao.CfPoHeaderMapper;
import com.chenfan.finance.dao.DownpaymentConfigMapper;
import com.chenfan.finance.enums.*;
import com.chenfan.finance.model.CfPoHeader;
import com.chenfan.finance.model.bo.AdvancePayBo;
import com.chenfan.finance.model.dto.AdvancepayApply;
import com.chenfan.finance.model.dto.GRole;
import com.chenfan.finance.model.vo.AdvancePayVo;
import com.chenfan.finance.model.vo.AdvanceVo;
import com.chenfan.finance.model.vo.PayApplyInfo;
import com.chenfan.finance.server.BaseInfoRemoteServer;
import com.chenfan.finance.server.BaseRemoteServer;
import com.chenfan.finance.server.PrivilegeUserServer;
import com.chenfan.finance.server.VendorCenterServer;
import com.chenfan.finance.server.remote.request.BrandFeignRequest;
import com.chenfan.finance.server.remote.vo.BrandFeignVO;
import com.chenfan.privilege.request.SUserVOReq;
import com.chenfan.privilege.response.SUserVORes;
import com.chenfan.vendor.response.VendorResModel;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author mbji
 */
@Service
public class AdvancePayHelper {


    @Autowired
    private CfPoHeaderMapper cfPoHeaderMapper;

    @Autowired
    private BaseRemoteServer baseRemoteServer;

    @Autowired
    private DownpaymentConfigMapper downpaymentConfigMapper;

    @Autowired
    private VendorCenterServer vendorCenterServer;

    @Autowired
    private PrivilegeUserServer privilegeUserServer;

    @Autowired
    private AdvancepayApplyMapper advancepayApplyMapper;

    @Autowired
    private BaseInfoRemoteServer baseInfoRemoteServer;

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancePayHelper.class);

    /**
     * ??????do
     *
     * @param bo
     * @param collect
     * @return
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     */
    public AdvancepayApply packAdvancepayApply(AdvancePayBo bo, List<GRole> collect, UserVO userVO) throws IllegalAccessException, InstantiationException {
        //????????????????????????????????????????????????????????????????????????????????????
        PayApplyInfo payApplyInfo = cfPoHeaderMapper.selectPayInfo(bo.getPoId());
        if (payApplyInfo.getVendorId() != null) {
            Response<VendorResModel> info = vendorCenterServer.getInfo(payApplyInfo.getVendorId());
            if (Objects.nonNull(info) && info.getCode() == ResponseCode.SUCCESS.getCode() && Objects.nonNull(info.getObj())) {
                if (null != bo.getBank()) {
                    payApplyInfo.setBank(bo.getBank());
                } else {
                    payApplyInfo.setBank(info.getObj().getCvenBank());
                    LOGGER.info("bank-", payApplyInfo.getBank());
                }
                if (null != bo.getBankAccount()) {
                    payApplyInfo.setBankAccount(bo.getBankAccount());
                } else {
                    payApplyInfo.setBankAccount(info.getObj().getCvenAccount());
                    LOGGER.info("bankAccount-", payApplyInfo.getBankAccount());
                }
                if (null != bo.getAccname()) {
                    payApplyInfo.setAccName(bo.getAccname());
                } else {
                    payApplyInfo.setAccName(info.getObj().getAccname());
                    LOGGER.info("accName-", payApplyInfo.getAccName());
                }
                payApplyInfo.setVendorCode(info.getObj().getVendorCode());
                payApplyInfo.setVendorName(info.getObj().getVendorName());
            }
        }
        //??????????????????????????????id????????????????????????id????????????
        AdvancepayApply advancepayApply = BeanMapper.map(bo, AdvancepayApply.class);
        //????????????id?????????????????????????????????????????????id??????????????????????????????
        BeanMapper.map(payApplyInfo, advancepayApply);
        //?????????????????????????????????????????????????????????????????????????????????
        advancepayApply.setMaterialType(payApplyInfo.getPoType());
        if (bo.getPaymentConfId() != null) {
            advancepayApply.setMoney(bo.getMoney());
        } else {
            advancepayApply.setMoney(bo.getMoney());
        }
        if (bo.getFirstOrLastPay() == null) {
            if (null == bo.getPayType()) {
                AdvanceVo advanceVo = advancepayApplyMapper.selectByPoId(bo.getPoCode());
                if (Objects.nonNull(advanceVo)) {
                    advancepayApply.setPaymentType(advanceVo.getPaymentType());
                }
            } else {
                advancepayApply.setPaymentType(bo.getPayType().toString());
            }
        } else {
            advancepayApply.setPaymentType(bo.getFirstOrLastPay().toString());
        }
        //????????????PaymentConfId
        if (null != bo.getPaymentConfId()) {
            advancepayApply.setPaymentConfId(bo.getPaymentConfId());
        }

        //????????????????????????
        advancepayApply.setMoneyCapital(NumberToStringForChineseMoney.getChineseMoneyStringForBigDecimal(advancepayApply.getMoney()));
        //??????????????????
        //??????????????????????????????????????????????????????????????????????????????
        GRole tsRole = collect.get(0);
        advancepayApply.setRoleId(tsRole.getRoleCode());
        //??????????????????
        advancepayApply.setFirstRoleId(tsRole.getRoleCode());
        //????????????
        advancepayApply.setDuties(tsRole.getPositionName());
        //?????????????????? shopType 1????????? ?????????????????? ????????????????????????????????????   ??????????????????????????????+AccName
        if (payApplyInfo.getAccName() == null) {
            payApplyInfo.setAccName("");
        }

        //??????????????????
        BrandFeignVO brandFeignVO = getBrandInfo(payApplyInfo.getBrandId());


        String brandType = "1";
        if (null != bo.getReceiptDepartment()) {
            advancepayApply.setReceiptDepartment(bo.getReceiptDepartment());
        } else {
            if (brandType.equals(brandFeignVO.getBrandType())) {
                advancepayApply.setReceiptDepartment(payApplyInfo.getVendorName());
            } else {
                advancepayApply.setReceiptDepartment(payApplyInfo.getVendorName() + payApplyInfo.getAccName());
            }
        }
        //}
        advancepayApply.setAdvancePayCode(baseRemoteServer.getPayNum(BillNoConstantClassField.ADVANCEPAY_APPLY_BUSINESS).getObj().toString());
        advancepayApply.setCreateBy(userVO.getUserId());
        advancepayApply.setCreateName(userVO.getRealName());
        advancepayApply.setTaskPerson(userVO.getRealName());
        advancepayApply.setAccname(payApplyInfo.getAccName());
        return advancepayApply;
    }

    private BrandFeignVO getBrandInfo(Integer brandId) {
        BrandFeignRequest brandFeignRequest = new BrandFeignRequest();
        List<Integer> brandIdList = new ArrayList<>();
        brandIdList.add(brandId);
        brandFeignRequest.setBrandIdList(brandIdList);
        Response<List<BrandFeignVO>> response = baseInfoRemoteServer.getBrandByBrandIdList(brandFeignRequest);
        if (HttpStateEnum.OK.getCode() != response.getCode()) {
            LOGGER.error("??????baseinfo??????????????????????????????,??????{}", JSONObject.toJSONString(brandFeignRequest));
            throw new BusinessException(response.getCode(), response.getMessage());
        }

        if (org.apache.commons.collections.CollectionUtils.isNotEmpty(response.getObj())) {
            return response.getObj().get(0);
        }
        throw new BusinessException(SystemState.BUSINESS_ERROR.code(), "???????????????,??????ID" + brandId);
    }

    /**
     * ??????
     *
     * @param roleCodes
     * @param bo
     * @param advancePayVo
     */
    public AdvancepayApply submit(List<String> roleCodes, AdvancePayBo bo, AdvancePayVo advancePayVo, UserVO userVO) throws PurchaseException {
        //??????????????????????????????
        if (!AdvencePayEnum.AUDIT.getCode().equals(advancePayVo.getState())) {
            throw new PurchaseException(ErrorMessageEnum.NO_POWER.getMsg());
        }
        List<RoleEnum> roleEnums = getRoleEnum(roleCodes, advancePayVo);
        if (CollectionUtils.isEmpty(roleEnums)) {
            throw new PurchaseException(ErrorMessageEnum.ONLY_TASKMAN.getMsg());
        }
        //??????????????????dto --?????????????????????????????????id??????????????????rolecode??? ???????????? ??????????????????
        AdvancepayApply advancepayApply = getAdvancepayApply(bo.getAdvancePayId(), RoleEnum.SUPPLYCHAIN_INTERN);
        advancepayApply.setSupplychainInternName(userVO.getRealName());
        advancepayApply.setSupplychainInternDate(new Date());
        return advancepayApply;
    }

    /**
     * ?????????
     *
     * @param roleCodes
     * @param bo
     * @param advancePayVo
     */
    public AdvancepayApply paid(List<String> roleCodes, AdvancePayBo bo, AdvancePayVo advancePayVo, UserVO userVO) throws PurchaseException {
        //??????????????????????????????
        if (!AdvencePayEnum.COMPLETE.getCode().equals(advancePayVo.getState())) {
            throw new PurchaseException(ErrorMessageEnum.NO_POWER.getMsg());
        }
        List<RoleEnum> roleEnums = getRoleEnum(roleCodes, advancePayVo);
        if (CollectionUtils.isEmpty(roleEnums)) {
            throw new PurchaseException(ErrorMessageEnum.ONLY_TASKMAN.getMsg());
        }
        //??????????????????dto --?????????????????????????????????id??????????????????rolecode??? ???????????? ??????????????????
        AdvancepayApply advancepayApply = getAdvancepayApply(bo.getAdvancePayId(), RoleEnum.CASHIER);
        advancepayApply.setCashier(userVO.getRealName());
        advancepayApply.setCashierDate(new Date());
        advancepayApply.setImgUrls(bo.getImgUrls());
        //???????????????????????????
        advancepayApply.setTaskPerson("");
        return advancepayApply;
    }

    /**
     * ???????????????dto
     *
     * @param payId    ????????????id
     * @param roleEnum ?????????????????????code
     * @return
     */
    public AdvancepayApply getAdvancepayApply(Integer payId, RoleEnum roleEnum) {
        if (roleEnum.getNextTaskPerson().length != 0) {
            Response<List<SUserVORes>> listResponse = privilegeUserServer.listUsers(SUserVOReq.builder().roleCode(String.valueOf(roleEnum.getNextTaskPerson()[0])).build());
            List<String> taskman = Lists.newArrayList();
            if (Objects.nonNull(listResponse) && listResponse.getCode() == ResponseCode.SUCCESS.getCode()) {
                List<SUserVORes> suserVoResList = listResponse.getObj();
                if (!CollectionUtils.isEmpty(suserVoResList)) {
                    taskman = suserVoResList.stream().map(SUserVORes::getUsername).collect(Collectors.toList());
                }
            }
            if (!CollectionUtils.isEmpty(taskman)) {
                return new AdvancepayApply(payId, roleEnum.getCode(), roleEnum.getAgreeState().getCode(), StringUtils.join(taskman, ","));
            }
        }
        return new AdvancepayApply(payId, roleEnum.getCode(), roleEnum.getAgreeState().getCode(), null);
    }

    /**
     * ???????????????dto
     *
     * @param payId    ????????????id
     * @param roleEnum ?????????????????????code
     * @return
     */
    public AdvancepayApply getAdvancepayApply(Integer payId, RoleEnum roleEnum, Integer taskPerson) {
        if (roleEnum.getNextTaskPerson().length != 0) {
            Response<List<SUserVORes>> listResponse = privilegeUserServer.listUsers(SUserVOReq.builder().roleCode(String.valueOf(taskPerson)).build());
            List<String> taskman = Lists.newArrayList();
            if (Objects.nonNull(listResponse) && listResponse.getCode() == ResponseCode.SUCCESS.getCode()) {
                List<SUserVORes> sUserVoResList = listResponse.getObj();
                if (!CollectionUtils.isEmpty(sUserVoResList)) {
                    taskman = sUserVoResList.stream().map(SUserVORes::getUsername).collect(Collectors.toList());
                }
            }
            if (!CollectionUtils.isEmpty(taskman)) {
                return new AdvancepayApply(payId, roleEnum.getCode(), roleEnum.getAgreeState().getCode(), StringUtils.join(taskman, ","));
            }
        }
        return new AdvancepayApply(payId, roleEnum.getCode(), roleEnum.getAgreeState().getCode(), null);
    }

    /**
     * ??????
     *
     * @param roleCodes
     * @param bo
     * @param advancePayVo
     */
    public AdvancepayApply financeAudit(List<String> roleCodes, AdvancePayBo bo, AdvancePayVo advancePayVo, UserVO userVO) throws PurchaseException {
        //??????????????????????????????
        if (!AdvencePayEnum.SUBMIT.getCode().equals(advancePayVo.getState())) {
            throw new PurchaseException(ErrorMessageEnum.NO_POWER.getMsg());
        }
        List<RoleEnum> roleEnums = getRoleEnum(roleCodes, advancePayVo);
        if (CollectionUtils.isEmpty(roleEnums)) {
            throw new PurchaseException(ErrorMessageEnum.ONLY_TASKMAN.getMsg());
        }
        //??????????????????dto --?????????????????????????????????id??????????????????rolecode??? ???????????? ??????????????????
        AdvancepayApply advancepayApply = getAdvancepayApply(bo.getAdvancePayId(), RoleEnum.FINANCE);
        advancepayApply.setFinaceName(userVO.getRealName());
        advancepayApply.setFinaceDate(new Date());
        return advancepayApply;
    }

    /**
     * ??????
     *
     * @param roleCodes
     * @param bo
     * @param advancePayVo
     */
    public AdvancepayApply reckeck(List<String> roleCodes, AdvancePayBo bo, AdvancePayVo advancePayVo, UserVO userVO) throws PurchaseException {
        //??????????????????????????????
        if (!AdvencePayEnum.FINANCE_AUDIT.getCode().equals(advancePayVo.getState())) {
            throw new PurchaseException(ErrorMessageEnum.NO_POWER.getMsg());
        }
        List<RoleEnum> roleEnums = getRoleEnum(roleCodes, advancePayVo);
        if (CollectionUtils.isEmpty(roleEnums)) {
            throw new PurchaseException(ErrorMessageEnum.ONLY_TASKMAN.getMsg());
        }
        //??????????????????dto --?????????????????????????????????id??????????????????rolecode??? ???????????? ??????????????????
        AdvancepayApply advancepayApply = getAdvancepayApply(bo.getAdvancePayId(), RoleEnum.FINANCE_GM);
        advancepayApply.setFinaceGMName(userVO.getRealName());
        advancepayApply.setFinaceGMDate(new Date());
        return advancepayApply;
    }

    /**
     * ??????
     *
     * @param roleCodes
     * @param bo
     * @param userVO
     */
    public AdvancepayApply audit(List<String> roleCodes, AdvancePayBo bo, AdvancePayVo advancePayVo, UserVO userVO) throws PurchaseException {
        if (!AdvencePayEnum.CONFIRM.getCode().equals(advancePayVo.getState())) {
            throw new PurchaseException(ErrorMessageEnum.NO_POWER.getMsg());
        }
        //???????????????????????????????????????????????????????????????????????????
        List<RoleEnum> roleEnums = getRoleEnum(roleCodes, advancePayVo);
        if (CollectionUtils.isEmpty(roleEnums)) {
            throw new PurchaseException(ErrorMessageEnum.ONLY_TASKMAN.getMsg());
        }
        //????????????????????????
        RoleEnum roleEnum = roleEnums.get(0);
        //??????????????????????????????????????????????????????
        AdvancepayApply advancepayApply = getAdvancepayApply(bo.getAdvancePayId(), roleEnum);
        advancepayApply.setVerifyDate(new Date());
        advancepayApply.setVerifyName(userVO.getRealName());
        setAuditPerson(userVO, roleEnum, advancepayApply);
        return advancepayApply;
    }

    public void setAuditPerson(UserVO userVO, RoleEnum roleEnum, AdvancepayApply advancepayApply) {
        switch (roleEnum) {
            case FIRST_GM:
                advancepayApply.setConfirmName(userVO.getRealName());
                advancepayApply.setConfirmDate(new Date());
                break;
            case FINANCE:
                advancepayApply.setFinaceName(userVO.getRealName());
                advancepayApply.setFinaceDate(new Date());
                break;
            default:
                break;
        }
    }

    /**
     * ???????????????????????????????????????code?????????????????????
     * ???????????????????????????????????????
     *
     * @param roleCodes
     * @param advancePayVo
     * @return
     */
    public List<RoleEnum> getRoleEnum(List<String> roleCodes, AdvancePayVo advancePayVo) {
        //??????????????????????????????????????????????????????????????????????????????
        List<RoleEnum> enumList = RoleEnum.getEnumsByState(AdvencePayEnum.getEnumByCodeAndState(advancePayVo.getState()), advancePayVo.getRoleId());
        RoleEnum roleEnum = null;
        List<RoleEnum> roleEnums = new ArrayList<>();
        //???????????????????????????????????????????????????????????????????????????????????????
        for (RoleEnum anEnum : enumList) {
            for (String roleCode : roleCodes) {
                if (anEnum.equals(RoleEnum.getEnumByCode(roleCode))) {
                    roleEnums.add(anEnum);
                }
            }
        }
        return roleEnums;
    }

    /**
     * ??????
     *
     * @param advancePayVo
     * @return
     */
    public AdvancepayApply reject(List<String> roleCodes, AdvancePayVo advancePayVo) throws PurchaseException {
        //???????????????????????????????????????????????????????????????????????????
        List<RoleEnum> roleEnums = getRoleEnum(roleCodes, advancePayVo);
        if (CollectionUtils.isEmpty(roleEnums)) {
            throw new PurchaseException(ErrorMessageEnum.ONLY_TASKMAN.getMsg());
        }
        AdvancepayApply advancepayApply = new AdvancepayApply(advancePayVo.getAdvancePayId(),
                advancePayVo.getFirstRoleId(), AdvencePayEnum.NOT_CONFIRM.getCode(),
                advancePayVo.getCreateName());
        advancepayApply.setApplyConfirmName(null);
        advancepayApply.setApplyConfirmDate(null);
        advancepayApply.setConfirmName(null);
        advancepayApply.setConfirmDate(null);
        advancepayApply.setSupplychainInternDate(null);
        advancepayApply.setSupplychainInternName(null);
        advancepayApply.setFinaceName(null);
        advancepayApply.setFinaceDate(null);
        advancepayApply.setFinaceGMName(null);
        advancepayApply.setFinaceGMDate(null);
        advancepayApply.setCooOrHelperName(null);
        advancepayApply.setCooOrHelperDate(null);
        advancepayApply.setImgUrls(null);
        return advancepayApply;
    }

    /**
     * ????????????
     *
     * @param roleCodes
     * @param bo
     * @param advancePayVo
     */
    public AdvancepayApply confirm(List<String> roleCodes, AdvancePayBo bo, AdvancePayVo advancePayVo, UserVO userVO) throws PurchaseException {
        if (!AdvencePayEnum.NOT_CONFIRM.getCode().equals(advancePayVo.getState())) {
            throw new PurchaseException(ErrorMessageEnum.NO_POWER.getMsg());
        }
        //???????????????????????????????????????????????????????????????????????????
        List<RoleEnum> roleEnums = getRoleEnum(roleCodes, advancePayVo);
        if (CollectionUtils.isEmpty(roleEnums)) {
            throw new PurchaseException(ErrorMessageEnum.ONLY_TASKMAN.getMsg());
        }
        //????????????????????????
        RoleEnum roleEnum = roleEnums.get(0);
        AdvancepayApply advancepayApply = getAdvancepayApply(bo.getAdvancePayId(), roleEnum);
        advancepayApply.setApplyConfirmDate(new Date());
        advancepayApply.setApplyConfirmName(userVO.getRealName());
        return advancepayApply;
    }

    /**
     * ????????????
     *
     * @param roleCodes
     * @param bo
     * @param advancePayVo
     */
    public AdvancepayApply close(List<String> roleCodes, AdvancePayBo bo, AdvancePayVo advancePayVo) throws PurchaseException {
        CfPoHeader poMain = cfPoHeaderMapper.selectByPrimaryKey(advancePayVo.getPoId());
        String zero = "0";
        String one = "1";
        if (poMain.getRetainage() != null && zero.equals(advancePayVo.getPaymentType())) {
            throw new PurchaseException("?????????????????????????????????????????????????????????????????????????????????");
        }
        //???????????????????????????????????????????????????????????????????????????
        List<RoleEnum> roleEnums = getRoleEnum(roleCodes, advancePayVo);
        if (CollectionUtils.isEmpty(roleEnums)) {
            throw new PurchaseException(ErrorMessageEnum.ONLY_TASKMAN.getMsg());
        }
        //??????????????????????????????????????????
        boolean checkType = zero.equals(advancePayVo.getPaymentType()) || one.equals(advancePayVo.getPaymentType());
        if (advancePayVo.getMoney() != null && checkType) {

            int update = cfPoHeaderMapper.updateByPrimaryKeySelective(new CfPoHeader(advancePayVo.getPoId(), new BigDecimal(-1), new BigDecimal(-1), new BigDecimal(-1), PayTypeEnum.getEnumByValue(Integer.parseInt(advancePayVo.getPaymentType()))));
            if (update == 0) {
                throw new PurchaseException(ErrorMessageEnum.ID_NOT_FOUND.getMsg());
            }
        }
        if (Integer.valueOf(advancePayVo.getPaymentType()) > 1) {
            int update = cfPoHeaderMapper.updateHirePurchase(advancePayVo.getPoId(), advancePayVo.getMoney(), 0);
            if (update == 0) {
                throw new PurchaseException(ErrorMessageEnum.ID_NOT_FOUND.getMsg());
            }
        }
        //???????????????
        return new AdvancepayApply(bo.getAdvancePayId(), roleCodes.get(0), AdvencePayEnum.CLOSE.getCode(), "");
    }
}
