package com.icbc.aiops.langfuse.api;

import javax.validation.constraints.NotBlank;

public class AamLoginRequest {
    @NotBlank private String aamId;
    @NotBlank private String ticket;
    public String getAamId() { return aamId; }
    public void setAamId(String aamId) { this.aamId = aamId; }
    public String getTicket() { return ticket; }
    public void setTicket(String ticket) { this.ticket = ticket; }
}
