package operato.fnf.wcs.service.board;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import operato.fnf.wcs.FnfUtils;
import operato.fnf.wcs.service.model.BoardCellSum;
import operato.fnf.wcs.service.model.BoardRackStock;
import operato.fnf.wcs.service.model.FloorTotalSum;
import xyz.anythings.base.model.ResponseObj;
import xyz.anythings.base.service.impl.AbstractLogisService;
import xyz.elidom.orm.IQueryManager;
import xyz.elidom.orm.manager.DataSourceManager;
import xyz.elidom.util.BeanUtil;
import xyz.elidom.util.ValueUtil;

@Component
public class GetFloorRackStock extends AbstractLogisService {
	public ResponseObj getFloorRackStock(Map<String, Object> params) throws Exception {
		String buildingTcd = String.valueOf(params.get("building_tcd"));
		String floorTcd = String.valueOf(params.get("floor_tcd"));	// "3F%"
		String brand = String.valueOf(params.get("brand"));
		String itemCd = String.valueOf(params.get("item_cd"));
		String itemGcd = String.valueOf(params.get("item_gcd"));
		String itemNm = String.valueOf(params.get("item_nm"));
		String season = String.valueOf(params.get("season"));
		String color = String.valueOf(params.get("color"));
		String style = String.valueOf(params.get("style"));
		String size = String.valueOf(params.get("size"));
		String assortYn = String.valueOf(params.get("assort_yn"));
		String assortCd = String.valueOf(params.get("assort_cd"));
		
		
		IQueryManager wmsQueryMgr = BeanUtil.get(DataSourceManager.class).getQueryManager("WMS");
		Map<String, Object> wmsParams = ValueUtil.newMap("building_tcd,floor_tcd", buildingTcd,floorTcd);
		if (ValueUtil.isNotEmpty(brand)) {
			wmsParams.put("brand", brand);
		}
		if (ValueUtil.isNotEmpty(itemCd)) {
			wmsParams.put("item_cd", itemCd);
		}
		if (ValueUtil.isNotEmpty(itemGcd)) {
			wmsParams.put("item_gcd", itemGcd);
		}
		if (ValueUtil.isNotEmpty(itemNm)) {
			wmsParams.put("item_nm", itemNm);
		}
		if (ValueUtil.isNotEmpty(season)) {
			wmsParams.put("season", season);
		}
		if (ValueUtil.isNotEmpty(color)) {
			wmsParams.put("color", color);
		}
		if (ValueUtil.isNotEmpty(style)) {
			wmsParams.put("style", style);
		}
		if (ValueUtil.isNotEmpty(size)) {
			wmsParams.put("size", size);
		}
		if (ValueUtil.isNotEmpty(assortYn)) {
			wmsParams.put("assort_yn", assortYn);
		}
		if (ValueUtil.isNotEmpty(assortCd)) {
			wmsParams.put("assort_cd", assortCd);
		}
		
		String sql = FnfUtils.queryCustServiceWithCheck("board_floor_rack_stock");	// detail
		List<BoardRackStock> list = wmsQueryMgr.selectListBySql(sql, wmsParams, BoardRackStock.class, 0, 0);
		
		Map<String, BoardCellSum> cellMap = new HashMap<>();
		for (BoardRackStock obj: list) {
			BoardCellSum cell = cellMap.get(obj.getLocation());
			if (ValueUtil.isEmpty(cell)) {
				cell = new BoardCellSum();
				cellMap.put(obj.getLocation(), cell);
				cell.setCapacity((float)obj.getSpaceCbm());
				cell.setUsed(obj.getUsedRate());	// 셀 사용율을 함수내에서 계산해줌.
			} else {
				cell.setCapacity(cell.getCapacity() + obj.getSpaceCbm());
				cell.setUsed(cell.getUsed() + obj.getUsedRate());	// 셀 사용율을 함수내에서 계산해줌.
			}
			if (ValueUtil.isNotEmpty(obj.getItemCd()) && !cell.getSkuCds().contains(obj.getItemCd())) {
				cell.addSkuCd(obj.getItemCd());
			}
		}
		
		String skuCntSql = FnfUtils.queryCustServiceWithCheck("board_floor_rack_sum");	// 층 summary
		FloorTotalSum floorTotalSum = wmsQueryMgr.selectBySql(skuCntSql, wmsParams, FloorTotalSum.class);
		
		Map<String, Object> values = new HashMap<>();
		values.put("cells", cellMap);
		values.put("floorSum", floorTotalSum);
		ResponseObj resp = new ResponseObj();
		resp.setItems(list);
		resp.setValues(values);
		return resp;
	}
}
