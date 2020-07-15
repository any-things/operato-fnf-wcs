package operato.fnf.wcs.service.rfid;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.fnf.wcs.entity.RfidBoxItem;
import xyz.anythings.base.model.ResponseObj;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.orm.IQueryManager;
import xyz.elidom.orm.manager.DataSourceManager;
import xyz.elidom.util.ValueUtil;

@Component
public class ShowIfPasdeliveryRecv extends AbstractQueryService {

	@Autowired
	private DataSourceManager dataSourceMgr;
	public ResponseObj showIfPasdeliveryRecv(Map<String, Object> params) {
		
		IQueryManager wmsQueryMgr = dataSourceMgr.getQueryManager(RfidBoxItem.class);
		
		
		String date = String.valueOf(params.get("date"));
		String sql = "select count(distinct no_box) from rfid_if.if_pasdelivery_recv where dt_delivery = :date and tp_machine = '2'";
		Integer boxNoCount = wmsQueryMgr.selectBySql(sql, ValueUtil.newMap("date", date), Integer.class);
		
		List<RfidBoxItem> list = wmsQueryMgr.selectListBySqlPath("operato/fnf/wcs/service/wms/recv_test.sql", ValueUtil.newMap("date", date), RfidBoxItem.class, 0, 0);
		
		Query conds = new Query(0, 1000);
		conds.addFilter("dtDelivery", date);
		//List<RfidBoxItem> list = wmsQueryMgr.selectList(RfidBoxItem.class, conds);
		
		ResponseObj resp = new ResponseObj();
		resp.setItems(list);
		resp.setValues(ValueUtil.newMap("boxNoCount", boxNoCount));
		return resp;
	}
}
