package operato.fnf.wcs.service.model;

public class FloorTotalSum {
	private Long totalCap;
	private Long boxQty;
	private Long pcsQty;
	private Long skuCount;
	private Float usedRate;
	
	public Long getTotalCap() {
		return totalCap;
	}
	public void setTotalCap(Long totalCap) {
		this.totalCap = totalCap;
	}
	public Long getBoxQty() {
		return boxQty;
	}
	public void setBoxQty(Long boxQty) {
		this.boxQty = boxQty;
	}
	public Long getPcsQty() {
		return pcsQty;
	}
	public void setPcsQty(Long pcsQty) {
		this.pcsQty = pcsQty;
	}
	public Long getSkuCount() {
		return skuCount;
	}
	public void setSkuCount(Long skuCount) {
		this.skuCount = skuCount;
	}
	public Float getUsedRate() {
		return usedRate;
	}
	public void setUsedRate(Float usedRate) {
		this.usedRate = usedRate;
	}
}