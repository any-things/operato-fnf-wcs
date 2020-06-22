SELECT
	SHIPTO_ID AS SHOP_CD,
	ITEM_CD AS SKU_CD,
	ITEM_NM AS SKU_NM,
	BARCODE2 AS SKU_BARCD,
	BARCODE AS SKU_BARCD2,
	ITEM_STYLE AS SKU_STYLE,
	ITEM_COLOR AS SKU_COLOR,
	ITEM_SIZE AS SKU_SIZE,
	RFID_ITEM_YN,
	COALESCE(SUM(CMPT_QTY), 0) AS PICKED_QTY,
	0 AS CONFIRM_QTY
FROM
	DPS_JOB_INSTANCES
WHERE
	WH_CD = 'ICF'
	#if($batchId)
	AND WORK_UNIT = :batchId
	#end
	#if($orderNo)
	AND REF_NO = :orderNo
	#end
	#if($invoiceId)
	AND WAYBILL_NO = :invoiceId
	#end
	#if($boxId)
	AND (BOX_NO = :boxId OR BOX_ID = :boxId)
	#end
	#if($status)
	AND STATUS = :status
	#end
GROUP BY
	SHIPTO_ID, ITEM_CD, ITEM_NM, BARCODE2, BARCODE, SKU_STYLE, SKU_COLOR, SKU_SIZE, RFID_ITEM_YN