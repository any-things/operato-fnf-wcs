package operato.logis.dps.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import operato.fnf.wcs.FnFConstants;
import operato.fnf.wcs.entity.DpsJobInstance;
import operato.fnf.wcs.entity.RfidResult;
import operato.fnf.wcs.entity.WmsExpressWaybillPrint;
import operato.fnf.wcs.entity.WmsMheItemBarcode;
import operato.fnf.wcs.service.send.DpsBoxSendService;
import operato.logis.dps.DpsCodeConstants;
import operato.logis.dps.DpsConstants;
import operato.logis.dps.model.DpsBatchInputableBox;
import operato.logis.dps.model.DpsBatchSummary;
import operato.logis.dps.model.DpsInspection;
import operato.logis.dps.model.DpsSinglePackJobInform;
import operato.logis.dps.model.DpsSinglePackSummary;
import operato.logis.dps.query.store.DpsBatchQueryStore;
import operato.logis.dps.service.api.IDpsJobStatusService;
import operato.logis.dps.service.api.IDpsPickingService;
import operato.logis.dps.service.util.DpsBatchJobConfigUtil;
import operato.logis.dps.service.util.DpsServiceUtil;
import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.event.rest.DeviceProcessRestEvent;
import xyz.anythings.base.model.BatchProgressRate;
import xyz.anythings.base.model.EquipBatchSet;
import xyz.anythings.base.service.impl.AbstractLogisService;
import xyz.anythings.sys.model.BaseResponse;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.elidom.dbist.dml.Page;
import xyz.elidom.orm.IQueryManager;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.entity.Domain;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ValueUtil;

/**
 * DPS 모바일 장비에서 요청하는 트랜잭션 이벤트 처리 서비스 
 * 
 * @author yang
 */
@Component
public class DpsDeviceProcessService extends AbstractLogisService {
	/**
	 * 배치 쿼리 스토어
	 */
	@Autowired
	private DpsBatchQueryStore dpsBatchQueryStore;
	/**
	 * DPS 피킹 서비스
	 */
	@Autowired
	private IDpsPickingService dpsPickingService;
	/**
	 * DPS 작업 현황 조회 서비스
	 */
	@Autowired
	private IDpsJobStatusService dpsJobStatusService;
	/**
	 * DPS 출고 검수 서비스
	 */
	@Autowired
	private DpsInspectionService dpsInspectionService;
	
	/**
	 * DPS 박스 전송 서비스
	 */
	@Autowired
	private DpsBoxSendService dpsBoxSendService;

	/*****************************************************************************************************
	 * 										작 업 진 행 율 A P I
	 *****************************************************************************************************
	/**
	 * DPS 배치 작업 진행율 조회 : 진행율 + 투입 순서 리스트
	 *  
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/batch_summary', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void batchSummaryEventProcess(DeviceProcessRestEvent event) {
		
		// 1. 파라미터
		Map<String, Object> params = event.getRequestParams();
		String equipType = params.get("equipType").toString();
		String equipCd = params.get("equipCd").toString();
		int limit = ValueUtil.toInteger(params.get("limit"));
		int page = ValueUtil.toInteger(params.get("page"));
		
		// 2. 배치 조회
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 배치 서머리 조회 
		DpsBatchSummary summary = this.getBatchSummary(batch, equipType, equipCd, limit, page);

		// 4. 이벤트 처리 결과 셋팅 
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, summary));
		event.setExecuted(true);
	}
	
	/**
	 * B2C 배치에 대한 진행율 조회 
	 * 
	 * @param batch
	 * @param equipType
	 * @param equipCd
	 * @param limit
	 * @param page
	 * @return
	 */
	private DpsBatchSummary getBatchSummary(JobBatch batch, String equipType, String equipCd, int limit, int page) {
		
		// 1. 작업 진행율 조회
		BatchProgressRate rate = this.dpsJobStatusService.getBatchProgressSummary(batch);
		
		// 2. 투입 정보 리스트 조회 
		Page<JobInput> inputItems = this.dpsJobStatusService.paginateInputList(batch, equipCd, null, page, limit);
		
		// 3. 파라미터
		Long domainId = batch.getDomainId();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,equipType", domainId, batch.getId(), equipType);
		
		if(ValueUtil.isNotEmpty(batch.getEquipCd())) {
			params.put("equipCd", equipCd);
		}
		
		// 4. 투입 가능 박스 수량 조회 
		Integer inputableBox = AnyEntityUtil.findItem(domainId, false, Integer.class, this.dpsBatchQueryStore.getBatchInputableBoxQuery(), params);
		
		// 5. 결과 리턴
		return new DpsBatchSummary(rate, inputItems, inputableBox);
	}
	
	/**
	 * 주문 상세 정보 조회 
	 *  
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/order_items', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void searchOrderItems(DeviceProcessRestEvent event) {
		
		// 1. 파라미터
		Map<String, Object> params = event.getRequestParams();
		String equipType = params.get("equipType").toString();
		String equipCd = params.get("equipCd").toString();
		String orderNo = params.get("orderNo").toString();
		
		// 2. 배치 조회
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 투입 상세 리스트 조회
		List<JobInstance> jobList = this.dpsJobStatusService.searchInputJobList(batch, ValueUtil.newMap("orderNo", orderNo));

		// 4. 이벤트 처리 결과 셋팅 
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, jobList));
		event.setExecuted(true);
	}
	
	/*****************************************************************************************************
	 * 									 박 스 투 입 A P I
	 *****************************************************************************************************
	
	/**
	 * DPS 박스 투입 (BOX or Tray)
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/box_requirement', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void getBoxRequirementList(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 진행 중인 배치가 있을때만 조회 
		if(!ValueUtil.isEmpty(batch)) {
			// 3.1. 호기별 배치 분리 여부
			// 투입 대상 박스 리스트 조회시 별도의 로직 처리 필요 
			boolean useSeparatedBatch = DpsBatchJobConfigUtil.isSeparatedBatchByRack(batch);
			
			String query = this.dpsBatchQueryStore.getBatchInputableBoxByTypeQuery();
			Map<String,Object> queryParams = ValueUtil.newMap("domainId,batchId,equipType",event.getDomainId(),batch.getId(),equipType);
			
			// 3.2. 호기가 분리된 배치의 경우 
			if(useSeparatedBatch) {
				queryParams.put("equipCd",equipCd);
			}
			
			List<DpsBatchInputableBox> inputableBoxs = AnyEntityUtil.searchItems(event.getDomainId(), true, DpsBatchInputableBox.class, query, queryParams);
			event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, inputableBoxs));
		} else {
			event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, null));
		}

		// 5. 이벤트 처리 결과 셋팅 
		event.setExecuted(true);
	}
	
	/**
	 * DPS 트레이 박스 투입
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/input_bucket', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void inputBucket(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipType = params.get("equipType").toString();
		String equipCd = params.get("equipCd").toString();
		String boxId = params.get("bucketCd").toString();
		int limit = ValueUtil.toInteger(params.get("limit"));
		int page = ValueUtil.toInteger(params.get("page"));
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 트레이 박스 투입
		this.dpsPickingService.inputEmptyBucket(batch, false, boxId);
		
		// 4. 배치 서머리 조회 
		DpsBatchSummary summary = this.getBatchSummary(batch, equipType, equipCd, limit, page);

		// 5. 이벤트 처리 결과 셋팅 
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, summary));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 작업 존 박스 도착
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/bucket_arrive', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void bucketArrive(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipType = params.get("equipType").toString();
		String equipCd = params.get("equipCd").toString();
		String stationCd = params.get("stationCd").toString();
		String boxId = params.get("bucketCd").toString();
				
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회
		Long domainId = event.getDomainId();
		EquipBatchSet equipBatch = DpsServiceUtil.findBatchByEquip(domainId, equipType, equipCd);
		JobBatch batch = equipBatch.getBatch();
		
		// 3.1 대기 상태인 투입 정보 조회 
		JobInput input = AnyEntityUtil.findEntityBy(domainId, false, JobInput.class, null, "batchId,equipType,equipCd,stationCd,boxId,status", batch.getId(), equipType, equipCd, stationCd, boxId, DpsCodeConstants.JOB_INPUT_STATUS_WAIT);
		
		// 3.2 없으면 진행 중인 투입 정보 조회 
		if(input == null) {
			input = AnyEntityUtil.findEntityBy(domainId, true, JobInput.class, null, "batchId,equipType,equipCd,stationCd,boxId,status", batch.getId(), equipType, equipCd, stationCd, boxId, DpsCodeConstants.JOB_INPUT_STATUS_RUN);
		}
		
		// 3.3 투입 정보 상태 업데이트 (WAIT => RUNNING)
		if(ValueUtil.isEqualIgnoreCase(input.getStatus(), DpsCodeConstants.JOB_INPUT_STATUS_WAIT)) {
			input.setStatus(DpsCodeConstants.JOB_INPUT_STATUS_RUN);
			this.queryManager.update(input, DpsConstants.ENTITY_FIELD_STATUS, DpsConstants.ENTITY_FIELD_UPDATER_ID, DpsConstants.ENTITY_FIELD_UPDATED_AT);
		}
		
		// 4. 표시기 점등을 위한 작업 데이터 조회
		List<JobInstance> jobList = this.dpsJobStatusService.searchPickingJobList(batch, stationCd, input.getOrderNo());
				
		// 5. 작업 데이터로 표시기 점등 & 작업 데이터 상태 및 피킹 시작 시간 등 업데이트 
		if(ValueUtil.isNotEmpty(jobList)) {
			this.serviceDispatcher.getIndicationService(batch).indicatorsOn(batch, false, jobList);
		}
		
		// 6. 도착한 박스 기준으로 태블릿에 표시할 투입 박스 리스트 조회  
		List<JobInput> inputList = this.dpsJobStatusService.searchInputList(batch, equipCd, stationCd, input.getId());
		
		// 7. 이벤트 처리 결과 셋팅 
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, inputList));
		event.setExecuted(true);
	}
	
	/*****************************************************************************************************
	 * 									 단 포 처 리 A P I
	 *****************************************************************************************************

	/**
	 * DPS 무오더 단포 피킹 처리
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/no_order_single_pack/pick', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void noOrderSinglePackPick(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		String jobDate = event.getRequestParams().get("jobDate").toString();
		jobDate = jobDate.replace(SysConstants.DASH, SysConstants.EMPTY_STRING);
		String skuCd = event.getRequestParams().get("skuCd").toString();
		String printerId = event.getRequestParams().get("printerId").toString();
		
		// 2. 상품 코드인지 RFID 코드인지 체크
		if(ValueUtil.isEmpty(skuCd) || skuCd.length() > 33) {
			throw ThrowUtil.newValidationErrorWithNoLog("스캔한 바코드가 유효하지 않습니다.");
		}
		
		boolean rfidFlag = skuCd.length() >= 30;
		String itemCd = null;
		String shopCd = null;
		
		// 3. RFID 코드인 경우 RFID 체크
		if(rfidFlag) {
			Map<String, Object> result = this.dpsBoxSendService.checkRfidId(skuCd, true);			
			// 리턴 파라미터 : OUT_CONFIRM, OUT_MSG, OUT_DEPART, OUT_ITEM_CD, OUT_STYLE, OUT_GOODS, OUT_COLOR, OUT_SIZE, OUT_BARCOD
			itemCd = ValueUtil.toString(result.get("OUT_ITEM_CD"));
		} else {
			itemCd = skuCd;
		}
		
		// 4. 상품 코드로 상품 조회
		IQueryManager wmsQueryMgr = this.getDataSourceQueryManager(WmsMheItemBarcode.class);
		String sql = "select * from mhe_item_barcode where item_cd = :itemCd";
		if(!rfidFlag) sql += " or barcode = :itemCd or barcode2 = :itemCd";
		WmsMheItemBarcode sku = wmsQueryMgr.selectBySql(sql, ValueUtil.newMap("itemCd", itemCd), WmsMheItemBarcode.class);

		// 5. 상품 바코드로 한 번 더 조회 
		if(!rfidFlag && sku == null) {
			sku = wmsQueryMgr.selectByCondition(WmsMheItemBarcode.class, ValueUtil.newMap("barcode2", itemCd));
		}
		
		// 6. 상품 조회가 안 되면 에러 
		if(sku == null) {
			throw ThrowUtil.newValidationErrorWithNoLog("스캔한 바코드로 상품을 찾을 수 없습니다.");
		}

		// 7. RFID 상품인 경우 상품 코드를 스캔하는 경우 에러 
		if(!rfidFlag && ValueUtil.isEqual(sku.getRfidItemYn(), LogisConstants.Y_CAP_STRING)) {
			// TODO WMS MHE_ITEM_BARCODE에 RFID_ITEM_YN 필드가 추가되면 아래 해제 
			//throw ThrowUtil.newValidationErrorWithNoLog("스캔한 상품은 RFID 상품이니 반드시 RFID 검수를 해야합니다.");
		}
				
		// 8. WMS 실적 전송
		String todayStr = DateUtil.todayStr("yyyyMMdd");
		String newBoxId = this.dpsBoxSendService.newBoxId(Domain.currentDomainId(), null, todayStr);
		this.dpsBoxSendService.sendSinglePackToWms(Domain.currentDomainId(), todayStr, jobDate, sku.getBrand(), sku.getItemCd(), newBoxId);
		
		// 9. WMS 송장 발행
		String invoiceId = this.dpsBoxSendService.newWaybillNo(newBoxId, true);
		sql = "select * from mps_express_waybill_print where wh_cd = :whCd and waybill_no = :invoiceId";
		WmsExpressWaybillPrint label = wmsQueryMgr.selectBySql(sql, ValueUtil.newMap("whCd,invoiceId", FnFConstants.WH_CD_ICF, invoiceId), WmsExpressWaybillPrint.class);

		// 10. RFID 코드인 경우 RFID 실적 전송 
		if(rfidFlag) {
			RfidResult rfidResult = new RfidResult();
			rfidResult.setRfidId(skuCd);
			rfidResult.setJobDate(jobDate);
			rfidResult.setBrandCd(sku.getBrand());
			rfidResult.setOrderNo(label.getOnlineOrderNo());
			rfidResult.setSkuCd(itemCd);
			rfidResult.setBoxId(newBoxId);
			rfidResult.setOrderQty(1);
			rfidResult.setInvoiceId(invoiceId);
			rfidResult.setShopCd(shopCd);
			this.dpsBoxSendService.sendSinglePackingToRfid(rfidResult);
			this.queryManager.insert(rfidResult);
		}
		
		// 11. 작업 데이터 생성 ...		
		DpsJobInstance job = new DpsJobInstance();
		job.setId(UUID.randomUUID().toString());
		job.setWhCd(FnFConstants.WH_CD_ICF);
		job.setWorkDate(todayStr);
		job.setOutbEctDate(jobDate);
		job.setRefNo(label.getOnlineOrderNo());
		job.setBoxId(newBoxId);
		job.setWaybillNo(invoiceId);
		job.setStrrId(sku.getBrand());
		job.setPackTcd("D");
		job.setMheNo("M1");
		job.setItemCd(sku.getItemCd());
		job.setBarcode2(sku.getBarcode2());
		job.setBarcode(sku.getBarcode());
		job.setBoxInputAt(new Date());
		job.setShiptoId(label.getShiptoId());
		job.setShiptoNm(label.getShiptoNm());
		job.setCmptQty(1);
		job.setPickQty(1);
		job.setItemColor(sku.getItemColor());
		job.setItemSeason(sku.getItemSeason());
		job.setItemSize(sku.getItemSize());
		job.setItemStyle(sku.getItemStyle());
		job.setMheDatetime(job.getBoxInputAt());
		job.setMheDrId(LogisConstants.NOT_AVAILABLE_CAP_STRING);
		job.setWorkUnit(LogisConstants.NOT_AVAILABLE_CAP_STRING);
		job.setCellCd(LogisConstants.NOT_AVAILABLE_CAP_STRING);
		job.setRfidItemYn(rfidFlag ? LogisConstants.Y_CAP_STRING : LogisConstants.N_CAP_STRING);
		job.setStatus(LogisConstants.JOB_STATUS_EXAMINATED);
		this.queryManager.insert(job);
		
		// 12. 송장 발행
		DpsInspection inspection = new DpsInspection();
		inspection.setBoxId(job.getBoxId());
		inspection.setInvoiceId(invoiceId);
		JobBatch batch = new JobBatch();
		batch.setDomainId(Domain.currentDomainId());
		this.dpsInspectionService.printInvoiceLabel(batch, inspection, printerId);
		
		// 13. 리턴 
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, job));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 오더 기반 단포 피킹 처리
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/single_pack/pick', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void singlePackPick(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		String jobId = event.getRequestParams().get("jobId").toString();

		// 2. 작업 데이터 조회 
		JobInstance job = AnyEntityUtil.findEntityById(true, JobInstance.class, jobId);
		
		// 3. 작업 배치 조회 
		JobBatch batch = AnyEntityUtil.findEntityById(true, JobBatch.class, job.getBatchId());
		
		// 4. 피킹 검수 설정 확인
		int resQty = job.getPickQty();
		if(DpsBatchJobConfigUtil.isPickingWithInspectionEnabled(batch)) {
			resQty = 1;
		}
		
		// 5. 확정 처리 
		this.dpsPickingService.confirmPick(batch, job, resQty);
		
		// 6. 작업 완료가 되었다면 단포 작업 현황 조회
		if(job.getPickedQty() >= job.getPickQty()) {
			// 상품에 대한 단포 작업 정보 조회 
			List<DpsSinglePackSummary> singlePackInfo = this.dpsJobStatusService.searchSinglePackSummary(batch, job.getSkuCd(), job.getBoxTypeCd(), job.getPickQty());
			// 처리 결과 설정
			DpsSinglePackJobInform result = new DpsSinglePackJobInform(singlePackInfo, null);
			event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, result));
			
		} else {
			// 처리 결과 설정 
			event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, job));
		}

		event.setExecuted(true);
	}
	
	/**
	 * DPS 단포 박스 투입
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/single_pack/box_input', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void singlePackBoxInput(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipType = params.get("equipType").toString();
		String equipCd = params.get("equipCd").toString();
		String skuCd = params.get("skuCd").toString();
		String boxId = params.get("bucketCd").toString();

		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회
		Long domainId = event.getDomainId();
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(domainId, equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 사용 박스 타입 작업 설정에서 조회
		String boxType = DpsBatchJobConfigUtil.getInputBoxType(batch);
		
		// 4. 단포 박스 투입 서비스 호출
		boolean isBox = ValueUtil.isEqualIgnoreCase(boxType, DpsCodeConstants.BOX_TYPE_BOX);
		JobInstance job = (JobInstance)this.dpsPickingService.inputSinglePackEmptyBucket(batch, isBox, skuCd, boxId);
		
		// 5. 상품에 대한 단포 작업 정보 조회 
		//List<DpsSinglePackSummary> singlePackInfo = this.dpsJobStatusService.searchSinglePackSummary(batch, skuCd, job.getBoxTypeCd(), job.getPickQty());
		//DpsSinglePackJobInform result = new DpsSinglePackJobInform(singlePackInfo, job);
		
		// 6. 이벤트 처리 결과 셋팅 
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, job));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 단포 상품 변경
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/single_pack/sku_change', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void singlePackSkuChange(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipType = params.get("equipType").toString();
		String equipCd = params.get("equipCd").toString();
		String skuCd = params.get("skuCd").toString();

		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 상품에 대한 단포 작업 정보 조회 
		List<DpsSinglePackSummary> singlePackInfo = this.dpsJobStatusService.searchSinglePackSummary(batch, skuCd, null, null);
		
		// 4. 이벤트 처리 결과 셋팅 
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, singlePackInfo));
		event.setExecuted(true);
	}

	/*****************************************************************************************************
	 * 										출 고 검 수 A P I
	 *****************************************************************************************************
	
	/**
	 * DPS 출고 검수를 위한 검수 정보 조회 - 박스 ID
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/find_by_box', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void findByBox(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String boxType = params.get("boxType").toString();
		String boxId = params.get("boxId").toString();
		boolean reprintMode = params.containsKey("reprintMode") ? ValueUtil.toBoolean(params.get("reprintMode")) : false;
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();

		// 3. 검수 정보 조회
		DpsInspection inspection = null;
		
		if(ValueUtil.isEqualIgnoreCase(boxType, LogisCodeConstants.BOX_TYPE_TRAY)) {
			inspection = this.dpsInspectionService.findInspectionByTray(batch, boxId, reprintMode, true);
		} else {
			inspection = this.dpsInspectionService.findInspectionByBox(batch, boxId, reprintMode, true);
		}

		// 3. 이벤트 처리 결과 셋팅  
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, inspection));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 출고 검수를 위해 송장 번호로 검수 정보 조회
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/find_by_invoice', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void findByInvoice(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String invoiceId = params.get("invoiceId").toString();
		boolean reprintMode = params.containsKey("reprintMode") ? ValueUtil.toBoolean(params.get("reprintMode")) : false;
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 검수 정보 조회
		DpsInspection inspection = this.dpsInspectionService.findInspectionByInvoice(batch, invoiceId, reprintMode, true);
		
		// 4. 이벤트 처리 결과 셋팅  
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, inspection));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 출고 검수를 위해 주문 번호로 검수 정보 조회
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/find_by_order', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void findByOrder(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String orderNo = params.get("orderNo").toString();
		boolean reprintMode = params.containsKey("reprintMode") ? ValueUtil.toBoolean(params.get("reprintMode")) : false;
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 검수 정보 조회
		DpsInspection inspection = this.dpsInspectionService.findInspectionByOrder(batch, orderNo, reprintMode, true);

		// 4. 이벤트 처리 결과 셋팅  
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, inspection));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 내품 검수 (RFID 검수)
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/by_item', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void doInspectBoxItem(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String rfidId = params.get("rfidId").toString();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
				
		// 3. RFID 상태값 체크 프로시져 호출
		Map<?, ?> result = this.dpsBoxSendService.checkRfidId(rfidId, false);
		String successYn = ValueUtil.toString(result.get("OUT_CONFIRM"));
		
		// 4. RFID가 문제가 없는 경우
		if(ValueUtil.isEqualIgnoreCase(successYn, LogisConstants.Y_CAP_STRING)) {
			RfidResult rfid = new RfidResult();
			rfid.setRfidId(rfidId);
			rfid.setBrandCd(ValueUtil.toString(result.get("OUT_DEPART")));
			rfid.setSkuCd(ValueUtil.toString(result.get("OUT_ITEM_CD")));
			event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, rfid));
			
		// 5. RFID가 문제가 있는 경우 
		} else {
			String errorMsg = ValueUtil.isNotEmpty(result.get("OUT_MSG")) ? result.get("OUT_MSG").toString() : "유효하지 않은 RFID!";
			event.setReturnResult(new BaseResponse(false, errorMsg, null));
		}
		
		event.setExecuted(true);
	}
	
	/**
	 * DPS 출고 검수 완료
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/finish', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void finishInspection(DeviceProcessRestEvent event) {
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String orderNo = params.get("orderNo").toString();
		String trayCd = params.get("trayCd").toString();
		String boxId = params.get("boxId").toString();
		String printerId = params.get("printerId").toString();
		List<Map<String, Object>> itemObjs = event.getRequestPostBody();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 검수 완료 처리
		BoxPack boxPack = new BoxPack();
		boxPack.setDomainId(batch.getDomainId());
		boxPack.setBatchId(batch.getId());
		boxPack.setOrderNo(orderNo);
		boxPack.setBoxTypeCd(trayCd);
		boxPack.setBoxId(boxId);		
		
		if(ValueUtil.isEmpty(itemObjs)) {
			this.dpsInspectionService.finishInspection(batch, boxPack, null, printerId);
		} else {
			this.dpsInspectionService.finishInspection(batch, boxPack, null, printerId, itemObjs);
		}

		// 4. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, boxPack));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 단포 송장 출력 
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/single_pack_print_invoice', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void printSingleInvoiceLabel(DeviceProcessRestEvent event) {
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String printerId = params.get("printerId").toString();
		String orderNo = params.get("orderNo").toString();
		String invoiceId = params.get("invoiceId").toString();
		
		// 2. 단포 정보 조회
		Map<String, Object> jobParams = ValueUtil.newMap("refNo,waybillNo", orderNo, invoiceId);
		DpsJobInstance jobInstance = this.queryManager.selectByCondition(DpsJobInstance.class, jobParams);
		DpsInspection inspection = new DpsInspection();
		inspection.setBoxId(jobInstance.getBoxId());
		inspection.setInvoiceId(invoiceId);
		JobBatch batch = new JobBatch();
		batch.setDomainId(Domain.currentDomainId());
		int printedCount = this.dpsInspectionService.printInvoiceLabel(batch, inspection, printerId);
		
		// 3. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, printedCount));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 송장 출력 
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/print_invoice', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void printInvoiceLabel(DeviceProcessRestEvent event) {
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String printerId = params.get("printerId").toString();
		String orderNo = params.get("orderNo").toString();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 검수 정보 모두 조회 - 송장 분할인 경우 한 주문에 송장이 여러 장일 수 있음 
		List<DpsInspection> inspectionList = this.dpsInspectionService.searchInspectionList(batch, orderNo, true, true);
		Integer printedCount = 0;
		
		if(ValueUtil.isNotEmpty(inspectionList)) {
			// 4. 송장 발행
			for(DpsInspection inspection : inspectionList) {
				printedCount += this.dpsInspectionService.printInvoiceLabel(batch, inspection, printerId);
			}
		}
				
		// 5. 이벤트 처리 결과 셋팅
		String message = printedCount == 1 ? LogisConstants.OK_STRING : "송장 분할되어 [" + printedCount + "] 장 출력되었습니다.";
		event.setReturnResult(new BaseResponse(true, message, printedCount));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 거래명세서 출력 
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/print_trade_statement', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void printTradeStatement(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String printerId = params.get("printerId").toString();
		String boxId = params.get("boxId").toString();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 박스 조회
		BoxPack boxPack = AnyEntityUtil.findEntityBy(event.getDomainId(), false, BoxPack.class, null, "batchId,boxId", batch.getId(), boxId);
		if(boxPack == null) {
			boxPack = AnyEntityUtil.findEntityBy(event.getDomainId(), false, BoxPack.class, null, "boxId", boxId);
		}
		
		// 4. 거래명세서 발행
		Integer printedCount = this.dpsInspectionService.printTradeStatement(batch, boxPack, printerId);
		
		// 5. 이벤트 처리 결과 셋팅  
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, printedCount));
		event.setExecuted(true);
	}
	
	/**
	 * DPS 송장 (박스) 분할
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/split_box', 'dps')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void splitBox(DeviceProcessRestEvent event) {
		
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String orderNo = params.get("orderNo").toString();
		String trayCd = params.get("trayCd").toString();
		String boxId = params.get("boxId").toString();
		String printerId = params.get("printerId").toString();
		List<Map<String, Object>> itemObjs = event.getRequestPostBody();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회 
		EquipBatchSet equipBatchSet = DpsServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 검수 완료 처리
		BoxPack boxPack = new BoxPack();
		boxPack.setDomainId(batch.getDomainId());
		boxPack.setBatchId(batch.getId());
		boxPack.setOrderNo(orderNo);
		boxPack.setBoxTypeCd(trayCd);
		boxPack.setBoxId(boxId);		
		
		if(ValueUtil.isEmpty(itemObjs)) {
			this.dpsInspectionService.finishInspection(batch, boxPack, null, printerId);
		} else {
			this.dpsInspectionService.finishInspection(batch, boxPack, null, printerId, itemObjs);
		}

		// 4. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, boxPack));
		event.setExecuted(true);
	}
	
}
