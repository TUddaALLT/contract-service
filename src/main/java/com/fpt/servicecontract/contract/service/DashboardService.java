package com.fpt.servicecontract.contract.service;

import com.fpt.servicecontract.utils.BaseResponse;

import java.awt.print.Pageable;
import java.util.Date;

public interface DashboardService {
    BaseResponse numberNewContract(Date fromDate, Date toDate);
    BaseResponse numberContractBySignStatus(String signStatus);
}
