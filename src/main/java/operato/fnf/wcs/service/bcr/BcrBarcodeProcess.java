package operato.fnf.wcs.service.bcr;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import operato.fnf.wcs.entity.DpsBcrIfData;
import operato.fnf.wcs.entity.DpsJobInstance;
import xyz.anythings.base.model.ResponseObj;
import xyz.anythings.base.service.impl.AbstractLogisService;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomValidationException;
import xyz.elidom.util.BeanUtil;
import xyz.elidom.util.ValueUtil;

@Component
public class BcrBarcodeProcess extends AbstractLogisService {
	private final String STATUS_FINISH = "F";
	public ResponseObj bcrBarcodeProcess(Map<String, Object> params) throws Exception {
		
		Query conds = new Query(0, 50);
		conds.addFilter("procYn", "N");
		List<DpsBcrIfData> list = queryManager.selectList(DpsBcrIfData.class, conds);
		
		for (DpsBcrIfData obj: list) {
			BeanUtil.get(BcrBarcodeProcess.class).processData(obj);
		}
		
		return new ResponseObj();
	}
	
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processData(DpsBcrIfData obj) throws Exception {
		try {
			Query conds = new Query(0, 1);
			conds.addFilter("waybillNo", obj.getWaybillNo());
			conds.addOrder("mheDatetime", false);
			DpsJobInstance dpsJobInstance = queryManager.selectByCondition(DpsJobInstance.class, conds);
			
			if (ValueUtil.isEmpty(dpsJobInstance)) {
				throw new ElidomValidationException("BcrBarcodeRead: boxId["+ obj.getWaybillNo() +"] not found~~");
			}
			dpsJobInstance.setBcrStatus(STATUS_FINISH);
			queryManager.update(dpsJobInstance, "bcrStatus");
			
			obj.setProcYn("Y");
			queryManager.update(obj, "procYn");
		} catch(Exception e) {
			logger.error("BcrBarcodeProcessJob error~~", e);
			obj.setProcYn("E");
			obj.setErrorMsg(e.getMessage());
			queryManager.update(obj, "procYn");
		}
	}
}
