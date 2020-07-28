SELECT
	C.DOMAIN_ID
	, COALESCE(MPO.BATCH_NO, :batch_id) AS BATCH_ID 
	, C.CHUTE_NO
	, COALESCE(MPO.ORDER_QTY, 0) AS ORDER_QTY
	, COALESCE(MPR.RESULT_QTY, 0) AS RESULT_QTY
	, COALESCE(MPR.DMG_QTY, 0) AS DMG_QTY
	, COALESCE(MPR.NEW_QTY, 0) AS NEW_QTY
FROM
	CHUTES C
LEFT OUTER JOIN
	(SELECT 
		BATCH_NO, CHUTE_NO, SUM(QTY) AS RESULT_QTY
		, SUM(DMG_QTY) AS DMG_QTY, SUM(NEW_QTY) AS NEW_QTY 
	FROM 
		MHE_PAS_RLST 
	WHERE 
		BATCH_NO IN ( :batchList ) 
	GROUP BY 
		BATCH_NO, CHUTE_NO
	) MPR
ON
	C.CHUTE_NO = MPR.CHUTE_NO
LEFT OUTER JOIN
	(SELECT 
		BATCH_NO, CHUTE_NO, SUM(ORDER_QTY) AS ORDER_QTY 
	FROM 
		MHE_PAS_ORDER
	WHERE 
		BATCH_NO IN ( :batchList ) 
	GROUP BY 
		BATCH_NO, CHUTE_NO
	) MPO
ON
	C.CHUTE_NO = MPO.CHUTE_NO