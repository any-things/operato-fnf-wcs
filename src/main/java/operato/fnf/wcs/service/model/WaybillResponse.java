package operato.fnf.wcs.service.model;

/**
 * 운송장 번호 요청에 대한 응답 모델
 * 
 * @author shortstop
 */
public class WaybillResponse {

	/**
	 * 성공 메시지
	 */
	private String sucessMsg;
	/**
	 * 운송장 번호 
	 */
	private String waybillNo;
	/**
	 * 에러 메시지 
	 */
	private String errorMsg;
	/**
	 * 에러 코드 
	 */
	private String errorCode;
	/**
	 * PrintId
	 */
	private String printId;
	
	public String getSucessMsg() {
		return sucessMsg;
	}
	
	public void setSucessMsg(String sucessMsg) {
		this.sucessMsg = sucessMsg;
	}
	
	public String getWaybillNo() {
		return waybillNo;
	}
	
	public void setWaybillNo(String waybillNo) {
		this.waybillNo = waybillNo;
	}
	
	public String getErrorMsg() {
		return errorMsg;
	}
	
	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}
	
	public String getErrorCode() {
		return errorCode;
	}
	
	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}
	
	public String getPrintId() {
		return printId;
	}
	
	public void setPrintId(String printId) {
		this.printId = printId;
	}

}
