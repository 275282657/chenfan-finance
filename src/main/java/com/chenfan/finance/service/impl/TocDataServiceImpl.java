package com.chenfan.finance.service.impl;

import cn.afterturn.easypoi.csv.entity.CsvImportParams;
import cn.afterturn.easypoi.csv.imports.CsvImportService;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chenfan.ccp.util.start.ApplicationContextUtil;
import com.chenfan.common.exception.SystemState;
import com.chenfan.common.vo.Response;
import com.chenfan.common.vo.ResponseCode;
import com.chenfan.finance.dao.*;
import com.chenfan.finance.enums.NumberEnum;
import com.chenfan.finance.enums.TocMappingTypeEnum;
import com.chenfan.finance.model.BaseGetBrandInfoListReqModel;
import com.chenfan.finance.model.BaseGetBrandInfoListResModel;
import com.chenfan.finance.model.BrandU8Mapping;
import com.chenfan.finance.model.TocReportRp;
import com.chenfan.finance.model.bo.*;
import com.chenfan.finance.model.dto.AlipayOriginExcelDTO;
import com.chenfan.finance.model.dto.TocReportDto;
import com.chenfan.finance.model.vo.AlipayOriginExportVo;
import com.chenfan.finance.model.vo.TocReportBySuccessExportVo;
import com.chenfan.finance.model.vo.TocReportByWeekExportVo;
import com.chenfan.finance.mq.TocU8ProduceService;
import com.chenfan.finance.producer.U8Produce;
import com.chenfan.finance.scheduled.TocOriginBatchStockOutMappingScheduled;
import com.chenfan.finance.server.BaseInfoRemoteServer;
import com.chenfan.finance.service.TocDataService;
import com.chenfan.finance.utils.*;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author Wen.Xiao
 * @Description //??????toc ????????????
 * @Date 2021/3/2  16:05
 * @Version 1.0
 */
@Slf4j
@Service
public class TocDataServiceImpl implements TocDataService {
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private BaseInfoRemoteServer baseInfoRemoteServer;
    @Resource
    private BrandU8MappingMapper brandU8MappingMapper;
    @Resource
    private TocIncomeOrderStockOutMapper tocIncomeOrderStockOutMapper;
    @Resource
    private TocIncomeOrderMapper tocIncomeOrderMapper;
    @Resource
    private TocU8DetailMapper tocU8DetailMapper;
    @Resource
    private TocAlipayOriginMapper tocAlipayOriginMapper;
    @Resource
    private TocExpendOrderStockOutMapper tocExpendOrderStockOutMapper;
    @Resource
    private TocExpendOrderMapper    tocExpendOrderMapper;
    @Resource
    private TocOriginBatchStockOutMappingScheduled tocOriginBatchStockOutMappingScheduled;
    @Resource
    TocBrandMappingMapper tocBrandMappingMapper;
    @Resource
    private TocU8HeaderMapper tocU8HeaderMapper;
    @Resource
    private TocU8ProduceService tocU8ProduceService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Value("${toc.upload.path:/toc}")
    private String zipUploadPath;

    @Override
    public Response importData(MultipartFile files, MultipartFile[] lasts) throws Exception {
        File pathFile = new File(zipUploadPath);
        if(!pathFile.exists()){
            pathFile.mkdirs();
        }
        List<File> todoFiles = new ArrayList<>();

        if(Objects.nonNull(lasts)&&lasts.length>0){
            for (MultipartFile file : lasts) {
                if (file == null) {
                    continue;
                }
                File newFile = new File(zipUploadPath ,file.getOriginalFilename());
                file.transferTo(newFile);
                todoFiles.add(newFile);
            }
        }
        if(Objects.nonNull(files)){
            if(files.getOriginalFilename().toUpperCase().endsWith("ZIP")){
                File newFile = new File(zipUploadPath , files.getOriginalFilename());
                //??????CommonsMultipartFile????????????????????????????????????????????????
                files.transferTo(newFile);
                File[] unzip = ZipUtils.unzip(newFile, zipUploadPath+"csv");
                newFile.delete();
                todoFiles.addAll(Arrays.asList(unzip));
            }else{
                File newFile = new File(zipUploadPath ,files.getOriginalFilename());
                files.transferTo(newFile);
                todoFiles.add(newFile);
            }
        }
        U8Produce.applicationContext.getBean(TocDataService.class).insertDataForFile(todoFiles);
        return Response.success("?????????????????????????????????????????????");
    }

    @Override
    @Async
    public void  insertDataForFile(List<File> files)  {
        LinkedList<Map<String, String>> ms = new LinkedList<>();
        for (File file : files) {
            if (file == null) {
                continue;
            }
            try {
                parseCVSandInsert(file, 4, ms);
            }catch (Exception e){
                log.error("??????????????????",e);
            }finally {
                file.delete();
            }

        }
        tocOriginBatchStockOutMappingScheduled.mappingBatchEntrance();
        log.info("?????????????????????{}",ms);
    }

    @Override
    public Response<PageInfo<TocReportRp.TocReportRpByWeek>> getDataOfWeek(TocReportDto.TocReportByWeek tocReportByWeek) {
        PageHelper.startPage(tocReportByWeek.getPageNum(), tocReportByWeek.getPageSize());
        PageInfo<TocReportRp.TocReportRpByWeek> pageDb = new PageInfo<>(tocU8DetailMapper.getDataOfWeek(tocReportByWeek));
        HashMap<Integer, String> brandNameById = this.getBrandNameById();
        for (TocReportRp.TocReportRpByWeek r:pageDb.getList()) {
            if(brandNameById.containsKey(r.getBrandId())){
                r.setBrandName(brandNameById.get(r.getBrandId()));
            }
        }
        return new Response<>(ResponseCode.SUCCESS,pageDb);
    }

    @Override
    public Response<PageInfo<TocReportRp.TocReportRpBySuccess>> getDataOfSuccess(TocReportDto.TocReportBySuccess tocReportBySuccess) {
        List<Integer> brandIds = tocReportBySuccess.getBrandIds();
        if(CollectionUtils.isNotEmpty(brandIds)){
            List<String> shopAccountByBrandIds = tocBrandMappingMapper.selectShopAccountByBrandIds(brandIds);
            tocReportBySuccess.setShopAccountList(CollectionUtils.isNotEmpty(shopAccountByBrandIds)?shopAccountByBrandIds:Arrays.asList("-1"));

        }
        PageHelper.startPage(tocReportBySuccess.getPageNum(), tocReportBySuccess.getPageSize());
        PageInfo<TocReportRp.TocReportRpBySuccess> pageDb = new PageInfo<>(tocAlipayOriginMapper.selectDataOfSuccess(tocReportBySuccess));
        List<TocReportRp.TocReportRpBySuccess> list = pageDb.getList();
        HashMap<Integer, String> brandNameById = this.getBrandNameById();
        for (TocReportRp.TocReportRpBySuccess r:list) {
            if(brandNameById.containsKey(r.getBrandId())){
                r.setBrandName(brandNameById.get(r.getBrandId()));
            }

        }
        return new Response<>(ResponseCode.SUCCESS,pageDb);
    }

    @Override
    public Response<PageInfo<TocAlipayOrigin>> getDataOfFailure(TocReportDto.TocReportByFailure tocReportByFailure) {
        List<Integer> brandIds = tocReportByFailure.getBrandIds();
        tocReportByFailure.setShopAccountList(tocBrandMappingMapper.selectShopAccountByBrandIds(brandIds));
        PageHelper.startPage(tocReportByFailure.getPageNum(), tocReportByFailure.getPageSize());
        PageInfo<TocAlipayOrigin> pageDb = new PageInfo<>(tocAlipayOriginMapper.selectListOfFail(tocReportByFailure));
        return new Response<>(ResponseCode.SUCCESS,pageDb);
    }

    @Override
    public Response<List<TocReportRp.TocReportRpByDetail>> getDataListOfId(Integer mappingDetailId) {
        List<TocReportRp.TocReportRpByDetail> tocReportRpByDetailList=new ArrayList<>();
        TocU8Detail tocU8Detail = tocU8DetailMapper.getOidsByMappingDetailId(mappingDetailId);
        String[] oids = tocU8Detail.getOids().split(",");
        if(BigDecimal.ZERO.compareTo(tocU8Detail.getSkuCount())>0){
            List<TocExpendOrder> tocExpendOrderList = tocExpendOrderMapper.selectListByOids(Arrays.asList(oids));
            tocReportRpByDetailList = BeanUtilCopy.copyListProperties(tocExpendOrderList, TocReportRp.TocReportRpByDetail::new);
            List<TocExpendOrderStockOut> tocExpendOrderStockOutList = tocExpendOrderStockOutMapper.selectListOfOids(Arrays.asList(oids));
            List<TocIncomeOrderStockOut> tocIncomeOrderStockOutList = BeanUtilCopy.copyListProperties(tocExpendOrderStockOutList, TocIncomeOrderStockOut::new);
            Map<String, List<TocIncomeOrderStockOut>> stringListMap = tocIncomeOrderStockOutList.stream().collect(Collectors.groupingBy(TocIncomeOrderStockOut::getOid));
            for (TocReportRp.TocReportRpByDetail tocReportRpByDetail:tocReportRpByDetailList) {
                if(stringListMap.containsKey(tocReportRpByDetail.getOid())){
                    List<TocIncomeOrderStockOut> mToc = stringListMap.get(tocReportRpByDetail.getOid());
                    tocReportRpByDetail.setOrderStockOuts(mToc);
                    BigDecimal stockOutQyt = mToc.stream().map(TocIncomeOrderStockOut::getStockOutNum).reduce(BigDecimal.ZERO, BigDecimal::add);
                    tocReportRpByDetail.setStockOutQyt(stockOutQyt);
                    tocReportRpByDetail.setStockOutNos(mToc.stream().map(x->x.getStockOutNo()).collect(Collectors.joining(",")));
                }
            }

        }else {
            List<TocIncomeOrder> tocIncomeOrderList = tocIncomeOrderMapper.selectListByOids(Arrays.asList(oids));
            tocReportRpByDetailList = BeanUtilCopy.copyListProperties(tocIncomeOrderList, TocReportRp.TocReportRpByDetail::new);
            List<TocIncomeOrderStockOut> tocIncomeOrderStockOutList = tocIncomeOrderStockOutMapper.selectListOfOids(Arrays.asList(oids));
            Map<String, List<TocIncomeOrderStockOut>> stringListMap = tocIncomeOrderStockOutList.stream().collect(Collectors.groupingBy(TocIncomeOrderStockOut::getOid));
            for (TocReportRp.TocReportRpByDetail tocReportRpByDetail:tocReportRpByDetailList) {
                if(stringListMap.containsKey(tocReportRpByDetail.getOid())){
                    List<TocIncomeOrderStockOut> mToc = stringListMap.get(tocReportRpByDetail.getOid());
                    tocReportRpByDetail.setOrderStockOuts(mToc);
                    BigDecimal stockOutQyt = mToc.stream().map(TocIncomeOrderStockOut::getStockOutNum).reduce(BigDecimal.ZERO, BigDecimal::add);
                    tocReportRpByDetail.setStockOutQyt(stockOutQyt);
                    tocReportRpByDetail.setStockOutNos(mToc.stream().map(x->x.getStockOutNo()).collect(Collectors.joining(",")));
                }
            }
        }
        return new Response<>(ResponseCode.SUCCESS,tocReportRpByDetailList);
    }

    /**
     * ????????????U8???????????????????????????????????????????????????????????????
     * @param tocReportByWeek
     * @param response
     */
    @Override
    public void getDataOfWeekOfExport(TocReportDto.TocReportByWeek tocReportByWeek, HttpServletResponse response) {
        Boolean aBoolean = this.checkReq(tocReportByWeek, response,false);
        if(aBoolean){
            try {
                List<Integer> brandIds = tocReportByWeek.getBrandIds();
                if(CollectionUtils.isNotEmpty(brandIds)){
                    List<String> shopAccountByBrandIds = tocBrandMappingMapper.selectShopAccountByBrandIds(brandIds);
                    tocReportByWeek.setShopAccountList(CollectionUtils.isNotEmpty(shopAccountByBrandIds)?shopAccountByBrandIds:Arrays.asList("-1"));
                }else {
                    List<String> collect = tocBrandMappingMapper.selectShopAccountAll();
                    tocReportByWeek.setShopAccountList(collect);
                }
                List<TocReportByWeekExportVo> tocReportByWeekExportVoList= tocU8DetailMapper.getDataOfWeekExportVo(tocReportByWeek);
                if(tocReportByWeekExportVoList!=null&&tocReportByWeekExportVoList.size()>100000){
                    PrintWriter writer = response.getWriter();
                    writer.print(JSONObject.toJSONString(Response.error(400,"????????????????????????????????????????????????????????????????????????")));
                    return;
                }
                FileUtil.exportExcelV2(
                        tocReportByWeekExportVoList,
                        "#?????????"+tocReportByWeek.getShopAccountList()+"",
                        "#???????????????[" + TimeFormatUtil.localDateTimeToStringAll(tocReportByWeek.getStartTime())+"]  ????????????["+TimeFormatUtil.localDateTimeToStringAll(tocReportByWeek.getEndTime())+"]",
                        tocReportByWeek.getShopAccountList()+"_??????u8??????",
                        TocReportByWeekExportVo.class,
                        tocReportByWeek.getShopAccountList()+"_??????u8??????.xlsx",
                        response);
            } catch (Exception e) {
                log.error("?????????????????????????????????????????????????????????????????????",e);

            }
        }
    }

    /**
     * ?????????????????????????????????????????????????????????????????????
     * @param tocReportBySuccess
     * @param response
     */
    @Override
    public void getDataOfSuccessOfExport(TocReportDto.TocReportBySuccess tocReportBySuccess, HttpServletResponse response) {
        Boolean aBoolean = this.checkReq(tocReportBySuccess, response,true);
        if(aBoolean){
            try {
                List<Integer> brandIds = tocReportBySuccess.getBrandIds();
                if(CollectionUtils.isNotEmpty(brandIds)){
                    List<String> shopAccountByBrandIds = tocBrandMappingMapper.selectShopAccountByBrandIds(brandIds);
                    tocReportBySuccess.setShopAccountList(CollectionUtils.isNotEmpty(shopAccountByBrandIds)?shopAccountByBrandIds:Arrays.asList("-1"));
                }else {
                    List<String> collect = tocBrandMappingMapper.selectShopAccountAll();
                    tocReportBySuccess.setShopAccountList(collect);
                }
                List<TocReportBySuccessExportVo> tocReportBySuccessExportVos = tocAlipayOriginMapper.selectDataOfSuccessExportVo(tocReportBySuccess);
                if(tocReportBySuccessExportVos!=null&&tocReportBySuccessExportVos.size()>100000){
                    PrintWriter writer = response.getWriter();
                    writer.print(JSONObject.toJSONString(Response.error(400,"????????????????????????????????????????????????????????????????????????")));
                    return;
                }
                FileUtil.exportExcelV2(
                        tocReportBySuccessExportVos,
                        "#?????????"+tocReportBySuccess.getShopAccountList()+"",
                        "#???????????????[" + TimeFormatUtil.localDateTimeToStringAll(tocReportBySuccess.getStartTime())+"]  ????????????["+TimeFormatUtil.localDateTimeToStringAll(tocReportBySuccess.getEndTime())+"]",
                        tocReportBySuccess.getShopAccountList()+"_mapping??????",
                        TocReportBySuccessExportVo.class,
                        tocReportBySuccess.getShopAccountList()+"_mapping??????.xlsx",
                        response);
            } catch (Exception e) {
                log.error("?????????????????????????????????????????????????????????????????????",e);

            }
        }
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????BI?????????????????????
     * @param tocReportByFailure
     * @param response
     */
    @Override
    public void getDataOfFailureOfExport(TocReportDto.TocReportByFailure tocReportByFailure, HttpServletResponse response) {
        Boolean aBoolean = this.checkReq(tocReportByFailure, response,false);
        if(aBoolean){
            try {
                List<Integer> brandIds = tocReportByFailure.getBrandIds();
                if(CollectionUtils.isNotEmpty(brandIds)){
                    List<String> shopAccountByBrandIds = tocBrandMappingMapper.selectShopAccountByBrandIds(brandIds);
                    tocReportByFailure.setShopAccountList(CollectionUtils.isNotEmpty(shopAccountByBrandIds)?shopAccountByBrandIds:Arrays.asList("-1"));
                }else {
                    List<String> collect = tocBrandMappingMapper.selectShopAccountAll();
                    tocReportByFailure.setShopAccountList(collect);
                }
                List<AlipayOriginExportVo> alipayOriginExportVos = tocAlipayOriginMapper.selectListOfFailExportVo(tocReportByFailure);
                if(alipayOriginExportVos!=null&&alipayOriginExportVos.size()>100000){
                    PrintWriter writer = response.getWriter();
                    writer.print(JSONObject.toJSONString(Response.error(400,"????????????????????????????????????????????????????????????")));
                    return;
                }
                FileUtil.exportExcelV2(
                        alipayOriginExportVos,
                        "??????????????????",
                        "#???????????????[" + TimeFormatUtil.localDateTimeToStringAll(tocReportByFailure.getStartTime())+"]  ????????????["+TimeFormatUtil.localDateTimeToStringAll(tocReportByFailure.getEndTime())+"]",
                        tocReportByFailure.getShopAccountList()+"??????????????????",
                        AlipayOriginExportVo.class,
                        tocReportByFailure.getShopAccountList()+"_??????????????????.xlsx",
                        response);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response pushDataToU8ByBrandId(List<Integer> brandIds,Date pushDate) {
        RLock muLock = redissonClient.getLock("C69EE00C169C50DA0C82A0EC6BEE6299");
        if(muLock.tryLock()){
            try {
                List<Integer> brandIdList=new ArrayList<>();
                if(CollectionUtils.isNotEmpty(brandIds)){
                    brandIdList=brandIds;
                }else {
                    brandIdList=tocBrandMappingMapper.selectOfList().stream().map(x->x.getBrandId()).collect(Collectors.toList());
                }
                if(CollectionUtils.isEmpty(brandIdList)){
                    return Response.error(500,"????????????????????????????????????");
                }
                RLock[] rLocks = new RLock[brandIdList.size()];;
                for(int i = 0,length = brandIdList.size(); i < length ;i ++){
                    RLock lock = redissonClient.getLock("pushDataToU8ByBrandId::"+brandIdList.get(i));
                    rLocks[i] = lock;
                }
                RLock multiLock = redissonClient.getMultiLock(rLocks);
                if(multiLock.tryLock()){
                    try {
                        HashMap<Integer,String> resultMap=new HashMap();
                        LocalDateTime localDateTime=null;
                        if(Objects.nonNull(pushDate)){
                            localDateTime=LocalDateTime.ofInstant(pushDate.toInstant(), ZoneId.systemDefault());
                        }else {
                            localDateTime= LocalDateTime.now().minusMonths(1);
                        }
                        LocalDateTime firstDayOfMonth =LocalDateTime.of(LocalDate.from(localDateTime.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate()), LocalTime.MIN);
                        LocalDateTime lastDayOfMonth = LocalDateTime.of(LocalDate.from(localDateTime.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate()), LocalTime.MAX);

                        Boolean allSuccess=true;
                        for (Integer brandId:brandIdList) {
                            HashMap<Integer, String> brandNameById = this.getBrandNameById();
                            Response<String> response = ApplicationContextUtil.getContext().getBean(TocDataServiceImpl.class).doPush(brandNameById,brandId, firstDayOfMonth, lastDayOfMonth).get();
                            if(response.getCode()!=SystemState.SUCCESS.code()){
                                allSuccess=false;
                            }
                            resultMap.put(brandId,response.getMessage());
                        }
                        return new Response(ResponseCode.SUCCESS.getCode(),String.valueOf(allSuccess),resultMap.values());
                    }catch (Exception e){
                        log.error("??????????????????",e);
                        return Response.error(SystemState.ERROR);
                    }finally {
                        multiLock.unlock();
                    }
                }
                return Response.error(SystemState.BUSINESS_ERROR.code(),"??????????????????????????????????????????");
            }finally {
                muLock.unlock();
            }

        }
        return Response.error(501,"??????????????????????????????????????????????????????");
    }
    @Async
    public ListenableFuture<Response> doPush(HashMap<Integer, String> brandNameById,Integer brandId,LocalDateTime firstDayOfMonth,LocalDateTime lastDayOfMonth){
        String brandName=brandNameById.get(brandId)==null?String.valueOf(brandId):brandNameById.get(brandId);
        List<Long> mappingIds=new ArrayList<>();
        List<String> shopAliPayAccounts =tocBrandMappingMapper.selectShopAccountByBrandIds(Arrays.asList(brandId));
        List<BrandU8Mapping> brandU8Mappings = brandU8MappingMapper.selectList(Wrappers.lambdaQuery(BrandU8Mapping.class)
                .eq(BrandU8Mapping::getBrandId, brandId));
        if(CollectionUtils.isEmpty(brandU8Mappings)){
            return new AsyncResult<>(Response.error(SystemState.BUSINESS_ERROR.code(),"??????:'"+brandName+"' ????????????U8??????")) ;
        }
        if(CollectionUtils.isEmpty(shopAliPayAccounts)){
            return new AsyncResult<>(Response.error(SystemState.BUSINESS_ERROR.code(),"??????:'"+brandName+"' ???????????????????????????")) ;
        }
        LocalDateTime startTime=firstDayOfMonth;
        {
            int week=0;
            LocalDateTime start=null;
            LocalDateTime end=null;
            a:for (int i = 1; i < 8 ; i++) {
                week=i;
                start =startTime.plusDays(7*(i-1));
                end=LocalDateTime.of(LocalDate.from(startTime.plusDays(7*i-1)), LocalTime.MAX);
                if(start.isBefore(lastDayOfMonth)&&end.isAfter(lastDayOfMonth)){
                    end=lastDayOfMonth;
                }else if(end.isAfter(lastDayOfMonth)&&start.isAfter(lastDayOfMonth)){
                    break a;
                }
                //????????????
                {
                    long mappingId = Long.parseLong(TimeFormatUtil.localDateTimeToString(startTime,"yyyyMM")+ week + 0 + brandId);
                    mappingIds.add(mappingId);
                    if(Objects.isNull(tocU8HeaderMapper.selectById(mappingId))){
                        List<TocU8Header> tocU8HeadersOfInto = tocU8HeaderMapper.selectList(Wrappers.<TocU8Header>lambdaQuery()
                                .eq(TocU8Header::getBrandId, brandId)
                                .eq(TocU8Header::getType, NumberEnum.ZERO.getCode())
                                .eq(TocU8Header::getMonth, TimeFormatUtil.localDateTimeToString(startTime,"yyyyMM"))
                                .eq(TocU8Header::getWeeknOfMonth, week));
                        if(CollectionUtils.isEmpty(tocU8HeadersOfInto)){
                            List<TocU8Detail> incomeList = tocIncomeOrderMapper.getIncome(start, end, shopAliPayAccounts);
                            List<TocU8Detail> incomeOfPost = tocIncomeOrderMapper.getIncomeOfPost(start, end, shopAliPayAccounts,
                                    Arrays.asList(TocMappingTypeEnum.INCOME_SHARE_AMOUNT_POST.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_REFUND_POST.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_REFUND_POST_OTHER.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_EQUITY_POST.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_EQUITY_POST_REFUND.getNo()
                                    ));
                            if(CollectionUtils.isNotEmpty(incomeOfPost)){
                                incomeList.addAll(incomeOfPost);
                            }
                            TocU8Header tocU8Header = this.buildU8OfIn(mappingId,incomeList, brandId, week, TimeFormatUtil.localDateTimeToString(startTime,"yyyyMM"),start,end);
                            if(!Objects.isNull(tocU8Header)){
                                this.insertTocU8(tocU8Header);
                            }
                        }
                    };

                }
                //????????????
                {
                    long mappingId = Long.parseLong(TimeFormatUtil.localDateTimeToString(startTime,"yyyyMM")+ week + 1 + brandId);
                    mappingIds.add(mappingId);
                    if(Objects.isNull(tocU8HeaderMapper.selectById(mappingId))){
                        List<TocU8Header> tocU8HeadersOfOut = tocU8HeaderMapper.selectList(Wrappers.<TocU8Header>lambdaQuery()
                                .eq(TocU8Header::getBrandId, brandId)
                                .eq(TocU8Header::getType, NumberEnum.ONE.getCode())
                                .eq(TocU8Header::getMonth, TimeFormatUtil.localDateTimeToString(startTime,"yyyyMM"))
                                .eq(TocU8Header::getWeeknOfMonth, week));
                        if(CollectionUtils.isEmpty(tocU8HeadersOfOut)){
                            List<TocU8Detail> expendOfRefundFinanceNo = tocExpendOrderMapper.getExpend(start, end, shopAliPayAccounts,Arrays.asList(TocMappingTypeEnum.EXPEND_SHARE_AMOUNT_ALL.getNo()));

                            List<TocU8Detail> expendOfOther = tocExpendOrderMapper.getExpendOfOther(start, end, shopAliPayAccounts, Arrays.asList(TocMappingTypeEnum.EXPEND_SHARE_AMOUNT_OTHER.getNo()));
                            if(CollectionUtils.isNotEmpty(expendOfOther)){
                                expendOfRefundFinanceNo.addAll(expendOfOther);
                            }
                            List<TocU8Detail> expendOfIncomeRefund = tocExpendOrderMapper.getExpendOfIncomeRefund(start, end, shopAliPayAccounts,
                                    Arrays.asList(
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_REFUND.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_REFUND_POST.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_EQUITY_REFUND.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_EQUITY_POST_REFUND.getNo()
                                    ));
                            if(CollectionUtils.isNotEmpty(expendOfIncomeRefund)){
                                expendOfRefundFinanceNo.addAll(expendOfIncomeRefund);
                            }
                            List<TocU8Detail> expendOfIncomeEquity = tocExpendOrderMapper.getExpendOfIncomeEquity(start, end, shopAliPayAccounts,
                                    Arrays.asList(
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_EQUITY.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_EQUITY_REFUND.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_EQUITY_POST.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_EQUITY_POST_REFUND.getNo()
                                    ));
                            if(CollectionUtils.isNotEmpty(expendOfIncomeEquity)){
                                expendOfRefundFinanceNo.addAll(expendOfIncomeEquity);
                            }
                            List<TocU8Detail> expendOfIncomeOther = tocExpendOrderMapper.getExpendOfIncomeOther(start, end, shopAliPayAccounts,
                                    Arrays.asList(TocMappingTypeEnum.INCOME_SHARE_AMOUNT_REFUND_OTHER.getNo(),
                                            TocMappingTypeEnum.INCOME_SHARE_AMOUNT_REFUND_POST_OTHER.getNo()

                                    ));
                            if(CollectionUtils.isNotEmpty(expendOfIncomeOther)){
                                expendOfRefundFinanceNo.addAll(expendOfIncomeOther);
                            }

                            TocU8Header tocU8Header = this.buildU8OfOut(mappingId,expendOfRefundFinanceNo, brandId, week, TimeFormatUtil.localDateTimeToString(startTime,"yyyyMM"),start,end);
                            if(!Objects.isNull(tocU8Header)){
                                this.insertTocU8(tocU8Header);
                            }
                        }
                    }

                }
            }

        }
        {
            //????????????????????????????????????????????????
            BigDecimal bigDecimalOfOrigin = tocAlipayOriginMapper.selectBySum(shopAliPayAccounts, firstDayOfMonth, lastDayOfMonth);
            BigDecimal bigDecimalOfPush = tocU8DetailMapper.selectAmountSum(mappingIds);
            List<TocU8Header> tocU8Headers = tocU8HeaderMapper.selectList(Wrappers.<TocU8Header>lambdaQuery().in(TocU8Header::getMappingId, mappingIds));
            if(CollectionUtils.isNotEmpty(tocU8Headers)){
                if(bigDecimalOfOrigin.compareTo(bigDecimalOfPush)==0){
                    for (Long mappingId:mappingIds){
                        TocU8Header tocU8Header = tocU8HeaderMapper.selectById(mappingId);
                        if(Objects.isNull(tocU8Header)||(Objects.nonNull(tocU8Header.getPushState())&&tocU8Header.getPushState()==1)){
                            continue;
                        }
                        List<TocU8Detail> tocU8Details = tocU8DetailMapper.selectByMappingId(mappingId);
                        tocU8Header.setSkuInfos(tocU8Details);
                        this.dopushv1(brandU8Mappings,tocU8Header);
                        tocU8Header.setPushState(1);
                        tocU8Header.setPushTime(LocalDateTime.now());
                        tocU8HeaderMapper.updateById(tocU8Header);
                        String oids = tocU8Header.getSkuInfos().stream().map(x -> x.getOids()).collect(Collectors.joining(","));
                        if(tocU8Header.getType()==0){
                            tocIncomeOrderMapper.updateOrderNo(Arrays.asList(oids.split(",")),tocU8Header.getOrderNo());
                        }else {
                            tocExpendOrderMapper.updateOrderNo(Arrays.asList(oids.split(",")),tocU8Header.getOrderNo());
                        }
                    }
                    return new AsyncResult<>(new Response(SystemState.SUCCESS.code(),"?????????'"+brandName+"' ??????????????????:'"+bigDecimalOfOrigin+"'"));
                }else {
                    return new AsyncResult<>(Response.error(SystemState.BUSINESS_ERROR.code(),"??????:'"+brandName+"' ???????????????'"+bigDecimalOfOrigin+"'????????????????????????'"+bigDecimalOfPush+"'?????????????????????????????????????????????????????????"));
                }
            }else {
                return new AsyncResult<>(Response.error(SystemState.BUSINESS_ERROR.code(),"??????:'"+brandName+"' ??????????????????????????????"));
            }

        }
    }


    /**
     * ?????????????????????
     * @param tocReportDtoBase
     * @param response
     * @return
     */
    private Boolean checkReq(TocReportDto.TocReportDtoBase tocReportDtoBase,  HttpServletResponse response,Boolean isCheckBrandIds){
        List<Integer> brandIds = tocReportDtoBase.getBrandIds();
        LocalDateTime endTime = tocReportDtoBase.getEndTime();
        LocalDateTime startTime = tocReportDtoBase.getStartTime();
        try {
            response.setCharacterEncoding("UTF-8");
            response.setHeader("content-Type", "application/vnd.ms-excel");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("????????????.xlsx", "UTF-8"));
            if(stringRedisTemplate.hasKey("C69EE00C169C50DA0C82A0EC6BEE6299")){
                PrintWriter writer = response.getWriter();
                writer.print(JSONObject.toJSONString(Response.error(400,"?????????????????????????????????????????????????????????")));
                return false;
            }
            if(isCheckBrandIds&&(CollectionUtils.isEmpty(brandIds)||brandIds.size()>1)){
                PrintWriter writer = response.getWriter();
                writer.print(JSONObject.toJSONString(Response.error(400,"???????????????????????????????????????????????????????????????????????????????????????????????????")));
                return false;
            }
            if(isCheckBrandIds&&brandIds.size()==1&&brandIds.get(0)==-1){
                PrintWriter writer = response.getWriter();
                writer.print(JSONObject.toJSONString(Response.error(400,"???????????????????????????????????????????????????????????????????????????????????????????????????")));
                return false;
            }
            if(Objects.isNull(startTime)||Objects.isNull(endTime)){
                PrintWriter writer = response.getWriter();
                writer.print(JSONObject.toJSONString(Response.error(400,"???????????????????????????????????????????????????????????????????????????????????????????????????")));
                return false;
            }
            if(endTime.toEpochSecond(ZoneOffset.of("+8"))-startTime.toEpochSecond(ZoneOffset.of("+8"))>60*60*24*31){
                PrintWriter writer = response.getWriter();
                writer.print(JSONObject.toJSONString(Response.error(400,"???????????????????????????????????????????????????????????????????????????????????????????????????")));
                return false;
            }
        }catch (Exception re){
            log.error("???????????????????????????????????????????????????????????????????????????BI?????????????????????",re);
        }
        return true;
    }

    private int parseCVSandInsert(File file, int setTitleRows, List<Map<String, String>> ms) throws IOException {
        String originalFilename = file.getName();
        int indexOf = originalFilename.indexOf("_");
        if(indexOf<0){
            ms.add(addMsg("?????????????????????????????????????????????", originalFilename));
            return NumberEnum.ZERO.getCode();
        }
        String shopAccount = originalFilename.substring(0, indexOf);
        log.info("???????????????{}",file.getName());
        List<AlipayOriginExcelDTO> fileModels = importCvs(file, AlipayOriginExcelDTO.class, setTitleRows,5);
        List<TocAlipayOrigin> alipays = new ArrayList<>(fileModels.size());
        for (AlipayOriginExcelDTO fileModel : fileModels) {
            TocAlipayOrigin alipayOrigin = convertToModel(fileModel);
            if(Objects.isNull(alipayOrigin)){
                continue;
            }
            if(Objects.isNull(alipayOrigin.getAccountDate())){
                alipayOrigin.setAccountDate(LocalDateTime.now().plusMonths(-1));
            }
            alipays.add(alipayOrigin);
        }
        long startTime = System.currentTimeMillis();
        int result = BatchInsertUtil.batchInsert(alipays, TocAlipayOriginMapper.class, "insertList",shopAccount);
        long endTime = System.currentTimeMillis();
        String msg = "????????????: " + result + " ???????????? " + (endTime - startTime) / 1000 + " s.";
        ms.add(addMsg(msg, file.getName()));
        return result;
    }
    private Map<String, String> addMsg(String msg, String name) {
        Map<String, String> d = new HashMap<>(4);
        d.put(name, msg);
        return d;
    }

    private TocAlipayOrigin convertToModel(AlipayOriginExcelDTO fileModel) {
        try {
            TocAlipayOrigin alipayOrigin = BeanUtilCopy.copyPropertiesIgnoreType(fileModel, TocAlipayOrigin.class);
            return alipayOrigin;
        } catch (Exception e) {
            log.error(fileModel.toString(), e);

        }
        return null;
    }

    public <T>  List<T> importCvs(File file, Class<T> clazz, int setTitleRows, int keyIndex) throws IOException {
        CsvImportParams params = new CsvImportParams(CsvImportParams.GBK);
        params.setTitleRows(setTitleRows);
        params.setKeyIndex(keyIndex);

        return new CsvImportService().readExcel(new FileInputStream(file), clazz, params, null);
    }

    /**
     * ???????????????
     * @param incomeList
     * @param brand
     * @param week
     * @param month
     * @param start
     * @param end
     * @return
     */
    public TocU8Header buildU8OfIn(Long mappingId,List<TocU8Detail> incomeList, int brand, int week,String month,LocalDateTime start,LocalDateTime end){
        if (CollectionUtils.isEmpty(incomeList)) {
            return null;
        }
        TocU8Header heder = new TocU8Header();
        heder.setDaozhangt(start);
        heder.setType(0);
        heder.setMonth(month);
        heder.setBrandId(brand);
        heder.setWeeknOfMonth(week);
        heder.setMappingId(mappingId);
        heder.setCountStartTime(start);
        heder.setCountEndTime(end);
        heder.setOrderNo("CK"+new StringBuilder(month.substring(2)).append(week).append("0000").toString());
        for (TocU8Detail c : incomeList) {
            if (StringUtils.isBlank(c.getSkuCode())) {
                c.setSkuCode("OTHER0001");
            }
            if(StringUtils.isBlank(c.getSpuCode())){
                c.setSpuCode("OTHER001");
            }
            c.setSkuPrice(c.getTotalMoney().divide(c.getSkuCount(),2, RoundingMode.HALF_UP));
            c.setDaozhangt(heder.getDaozhangt());
            c.setMappingId(heder.getMappingId());

        }
        heder.setSkuInfos(incomeList);
        return heder;
    }

    /**
     * ????????????
     * @param expendList
     * @param brand
     * @param week
     * @param month
     * @param start
     * @param end
     * @return
     */
    public TocU8Header buildU8OfOut(Long mappingId,List<TocU8Detail> expendList, int brand, int week,String month,LocalDateTime start,LocalDateTime end){
        if (CollectionUtils.isEmpty(expendList)) {
            return null;
        }
        TocU8Header tocU8Header = new TocU8Header();
        tocU8Header.setDaozhangt(start);
        tocU8Header.setType(1);
        tocU8Header.setMonth(month);
        tocU8Header.setBrandId(brand);
        tocU8Header.setWeeknOfMonth(week);
        tocU8Header.setMappingId(mappingId);
        tocU8Header.setCountStartTime(start);
        tocU8Header.setCountEndTime(end);
        tocU8Header.setOrderNo("CK"+new StringBuilder(month.substring(2)).append(week).append("0001").toString());
        for (TocU8Detail c : expendList) {
            if (StringUtils.isBlank(c.getSkuCode())) {
                c.setSkuCode("OTHER0001");
            }
            if(StringUtils.isBlank(c.getSpuCode())){
                c.setSpuCode("OTHER001");
            }
            c.setSkuPrice(c.getTotalMoney().divide(c.getSkuCount(),2, RoundingMode.HALF_UP));
            c.setDaozhangt(tocU8Header.getDaozhangt());
            c.setMappingId(tocU8Header.getMappingId());

        }
        tocU8Header.setSkuInfos(expendList);
        return tocU8Header;
    }

    /**
     * ??????????????????????????????MQ
     * @param brandU8Mappings
     * @param tocU8Header
     */
    private void dopushv1(List<BrandU8Mapping> brandU8Mappings, TocU8Header tocU8Header) {
        for (BrandU8Mapping d : brandU8Mappings) {
            String db = d.getPDbV3() == null ? d.getPDbV2() : d.getPDbV3();
            tocU8Header.setU8db(db);
            tocU8ProduceService.syncTocMq(tocU8Header);

        }
    }

    /**
     * ???????????????
     * @param heder
     */
    private void insertTocU8(TocU8Header heder) {
        tocU8HeaderMapper.insert(heder);
        BatchInsertUtil.batchInsert(heder.getSkuInfos(), TocU8DetailMapper.class,"insertList");
    }


    private HashMap<Integer,String> getBrandNameById(){
        HashMap<Integer,String> objectObjectHashMap = new HashMap<>();
        Response<List<BaseGetBrandInfoListResModel>> branInfoList = baseInfoRemoteServer.getBranInfoList(new BaseGetBrandInfoListReqModel());
        List<BaseGetBrandInfoListResModel> obj = branInfoList.getObj();
        for (BaseGetBrandInfoListResModel baseGetBrandInfoListResModel:obj){
            objectObjectHashMap.put(baseGetBrandInfoListResModel.getBrandId(),baseGetBrandInfoListResModel.getBrandName());
        }
        return objectObjectHashMap;
    }

}
