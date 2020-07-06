package operato.fnf.wcs.service.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import operato.fnf.wcs.FnFConstants;
import operato.fnf.wcs.entity.WcsMheDr;
import operato.fnf.wcs.entity.WcsMheDrRecall;
import operato.fnf.wcs.entity.WcsMheHr;
import operato.fnf.wcs.entity.WcsMheHrRecall;
import operato.fnf.wcs.entity.WmsMheHr;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.model.ResponseObj;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.orm.IQueryManager;
import xyz.elidom.util.DateUtil;
import xyz.elidom.util.ValueUtil;

@Component
public class DasRecallBatchService extends AbstractQueryService {
	private final String WMS_JOB_TYPE = "SHIPBYDAS";
	
	public ResponseObj dasRecallBatchService(Map<String, String> map) {
		String workDate = String.valueOf(map.get("workDate"));
		if (ValueUtil.isEmpty(workDate)) {
			workDate = DateUtil.getCurrentDay();
		}
		
		// 1. 회수주문 WmsMheHr를 조회
		Query conds = new Query();
		conds.addFilter("whCd", FnFConstants.WH_CD_ICF);
		conds.addFilter("bizType", WMS_JOB_TYPE);
		conds.addFilter("workDate", workDate);
		conds.addFilter("delYn", "Y");
		IQueryManager wmsQueryMgr = this.getDataSourceQueryManager(WmsMheHr.class);
		List<WmsMheHr> delWaveList = wmsQueryMgr.selectList(WmsMheHr.class, conds);
		
		// 2. 회수주문 있는지 여부 판단
		if (delWaveList.size() == 0) {
			ResponseObj resp = new ResponseObj();
			resp.setMsg("There is no recall order~~");
			return resp;
		}
		
		// 3. 회수된 주문을 조회, 회수 가능한 상태인지를 판단
		for (WmsMheHr delWave: delWaveList) {
			Query wcsHrConds = new Query();
			wcsHrConds.addFilter("whCd", FnFConstants.WH_CD_ICF);
			wcsHrConds.addFilter("workUnit", delWave.getWorkUnit());
			wcsHrConds.addFilter("bizType", WMS_JOB_TYPE);
			wcsHrConds.addFilter("status", "A");
			WcsMheHr rcWave = queryManager.selectWithLock(WcsMheHr.class, wcsHrConds);

			if (ValueUtil.isEmpty(rcWave)) {
				continue;
			}
			
			this.processRecall(rcWave);
		}
		
		return new ResponseObj();
	}
	
	private void processRecall(WcsMheHr wcsMheHr) {
		// 회수된 주문을 backup
		// insertBatch
		WcsMheHrRecall mheHrRc = ValueUtil.populate(wcsMheHr, new WcsMheHrRecall());
		queryManager.insert(mheHrRc);
		// 회수된 주문을 삭제 (MheHr, MhrDr)
		
		Query wcsDrConds = new Query();
		wcsDrConds.addFilter("whCd", FnFConstants.WH_CD_ICF);
		wcsDrConds.addFilter("workDate", wcsMheHr.getWorkDate());
		wcsDrConds.addFilter("workUnit", wcsMheHr.getWorkUnit());
		List<WcsMheDr> rcDetailList = queryManager.selectList(WcsMheDr.class, wcsDrConds);
		
		List<WcsMheDrRecall> MheDrRcs = new ArrayList<>();
		for (WcsMheDr obj: rcDetailList) {
			WcsMheDrRecall mheDrRc = ValueUtil.populate(obj, new WcsMheDrRecall());
			MheDrRcs.add(mheDrRc);
		}
		
		queryManager.insertBatch(MheDrRcs);
		queryManager.deleteBatch(rcDetailList);
		queryManager.delete(wcsMheHr);
		
		queryManager.delete(JobBatch.class, wcsMheHr.getWorkUnit());
	}
}