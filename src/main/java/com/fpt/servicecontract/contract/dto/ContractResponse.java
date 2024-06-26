package com.fpt.servicecontract.contract.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractResponse {
    private String id;
    private String name;
    private String createdBy;
    private String file;
    private String createdDate;
    private String status;
    private boolean isUrgent;
    private boolean canSend;
    private boolean canSendForMng ;
    private boolean canResend ;
    private boolean sign ;
    private boolean isApproved ;
    private String approvedBy;
    private String statusCurrent;
    private boolean canUpdate ;
    private boolean canDelete ;
    private boolean canUpdateContractRecieve;
}
