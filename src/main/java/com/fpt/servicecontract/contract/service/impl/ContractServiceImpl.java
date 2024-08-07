package com.fpt.servicecontract.contract.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.servicecontract.auth.model.Permission;
import com.fpt.servicecontract.auth.model.User;
import com.fpt.servicecontract.auth.repository.UserRepository;
import com.fpt.servicecontract.config.JwtService;
import com.fpt.servicecontract.config.MailService;
import com.fpt.servicecontract.contract.dto.*;
import com.fpt.servicecontract.contract.dto.request.ContractRequest;
import com.fpt.servicecontract.contract.dto.request.PartyRequest;
import com.fpt.servicecontract.contract.dto.response.ContractResponse;
import com.fpt.servicecontract.contract.dto.response.SignContractResponse;
import com.fpt.servicecontract.contract.enums.SignContractStatus;
import com.fpt.servicecontract.contract.model.Contract;
import com.fpt.servicecontract.contract.model.Party;
import com.fpt.servicecontract.contract.model.ContractStatus;
import com.fpt.servicecontract.contract.model.*;
import com.fpt.servicecontract.contract.repository.PartyRepository;
import com.fpt.servicecontract.contract.repository.ContractRepository;
import com.fpt.servicecontract.contract.repository.ContractStatusRepository;
import com.fpt.servicecontract.contract.service.*;
import com.fpt.servicecontract.utils.*;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final PartyRepository partyRepository;
    private final PdfUtils pdfUtils;
    private final CloudinaryService cloudinaryService;
    private final ContractHistoryService contractHistoryService;
    private final ElasticSearchService elasticSearchService;
    private final ContractStatusRepository contractStatusRepository;
    private final ContractStatusService contractStatusService;
    private final ContractTypeService contractTypeService;
    private final NotificationService notificationService;
    private final JwtService jwtService;
    private final MailService mailService;
    private final UserRepository userRepository;


    @Override
    @Transactional
    public BaseResponse createContract(ContractRequest contractRequest, String email) {
        Party partyA = getDefaultParty();
        Party partyB = Party
                .builder()
                .id(contractRequest.getPartyB().getId())
                .address(contractRequest.getPartyB().getAddress())
                .name(contractRequest.getPartyB().getName())
                .taxNumber(contractRequest.getPartyB().getTaxNumber())
                .presenter(contractRequest.getPartyB().getPresenter())
                .businessNumber(contractRequest.getPartyB().getBusinessNumber())
                .bankId(contractRequest.getPartyB().getBankId())
                .bankName(contractRequest.getPartyB().getBankName())
                .bankAccOwer(contractRequest.getPartyB().getBankAccOwer())
                .email(contractRequest.getPartyB().getEmail())
                .position(contractRequest.getPartyB().getPosition())
                .typeParty(false)
                .build();
        try {
            partyB = partyRepository.save(partyB);
//            partyA = partyRepository.save(partyA);
        } catch (Exception e) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, "Failed", false, e.getMessage());
        }
        Contract contract = Contract
                .builder()
                .id(contractRequest.getId())
                .name(contractRequest.getName())
                .number(contractRequest.getNumber())
                .rule(contractRequest.getRule())
                .createdBy(email)
                .term(contractRequest.getTerm())
                .partyAId(partyA.getId())
                .partyBId(partyB.getId())
                .status(Constants.STATUS.NEW)
                .isUrgent(contractRequest.isUrgent())
                .contractTypeId(contractRequest.getContractTypeId())
                .value(contractRequest.getValue())
                .updatedDate(LocalDateTime.now())
                .createdDate(LocalDateTime.now())
                .build();
        Context context = new Context();
        context.setVariable("partyA", partyA);
        context.setVariable("partyB", partyB);
        context.setVariable("info", contract);
        context.setVariable("date", LocalDateTime.now().toLocalDate());
        String html = pdfUtils.templateEngine().process("templates/new_contract.html", context);
        File file = null;
        try {
            file = pdfUtils.generatePdf(html, contract.getName() + "_" + UUID.randomUUID());
        } catch (Exception e) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
        }
        try {
            contract.setFile(cloudinaryService.uploadPdf(file));
        } catch (IOException e) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
        }
        if (file.exists() && file.isFile()) {
            boolean deleted = file.delete();
            if (!deleted) {
                log.warn("Failed to delete the file: {}", file.getAbsolutePath());
            }
        }
        Contract result;
        if (contractRequest.getId() == null) {
            contract.setStatus(Constants.STATUS.NEW);
            contract.setCreatedDate(LocalDateTime.now());
            try {
                result = contractRepository.save(contract);
            } catch (Exception e) {
                return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
            }
            contractHistoryService.createContractHistory(result.getId(), contract.getName(), contract.getCreatedBy(), "", Constants.STATUS.NEW);
            contractStatusService.create(email, null, result.getId(), Constants.STATUS.NEW, "new contract");
        } else {
            contract.setUpdatedDate(LocalDateTime.now());
            contractHistoryService.createContractHistory(contract.getId(), contract.getName(), contract.getCreatedBy(), "", Constants.STATUS.UPDATE);
            result = contractRepository.save(contract);
            String[] emails = new String[]{email};
            try {
                mailService.sendNewMail(emails, null, "Contract Update", "<h1>The contract have been updated<h1>", null);
            } catch (MessagingException e) {
                return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
            }
        }
        contractRequest.setId(result.getId());
        contractRequest.setSignA("");
        contractRequest.setSignB("");
        contractRequest.setContractTypeId(contractTypeService.getContractTypeById(contractRequest.getContractTypeId()).get().getTitle());
        try {
            elasticSearchService.indexDocument("contract", contractRequest, ContractRequest::getId);
        } catch (IOException e) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
        }
        return new BaseResponse(Constants.ResponseCode.SUCCESS, "Successfully", true, contract);
    }

    @Override
    public BaseResponse findAll(Pageable p, String email, String statusSearch, String search) {
        List<String> ids = contractStatusRepository.findAll().stream()
                .filter(m -> !ObjectUtils.isEmpty(m.getReceiver()) && m.getReceiver().contains(email) || !ObjectUtils.isEmpty(m.getSender()) && m.getSender().equals(email))
                .map(ContractStatus::getContractId)
                .toList();
        List<String> statusListSearch = getListStatusSearch(statusSearch);
        Page<Object[]> page = contractRepository.findAllContract(p, email, ids, statusListSearch, QueryUtils.appendPercent(search));
        List<ContractResponse> responses = new ArrayList<>();
        for (Object[] obj : page) {
            ContractResponse response = ContractResponse.builder()
                    .name(Objects.nonNull(obj[0]) ? obj[0].toString() : null)
                    .createdBy(Objects.nonNull(obj[1]) ? obj[1].toString() : null)
                    .file(Objects.nonNull(obj[2]) ? obj[2].toString() : null)
                    .createdDate(Objects.nonNull(obj[3]) ? obj[3].toString() : null)
                    .id(Objects.nonNull(obj[4]) ? obj[4].toString() : null)
                    .status(Objects.nonNull(obj[5]) ? obj[5].toString() : null)
                    .isUrgent(Objects.nonNull(obj[6]) && Boolean.parseBoolean(obj[6].toString()))
                    .approvedBy(Objects.nonNull(obj[7]) ? obj[7].toString() : null)
                    .statusCurrent(Objects.nonNull(obj[8]) ? obj[8].toString() : null)
                    .customer(Objects.nonNull(obj[9]) ? obj[9].toString() : null)
                    .canSend(true)
                    .canApprove(false)
                    .canSign(true)
                    .canSendForCustomer(true)
                    .value(Objects.nonNull(obj[12]) ? (Double) obj[12] : null)
                    .canRejectSign(false)
                    .build();
            String status = response.getStatusCurrent();
//            List<String> statusList = contractStatusService.checkDoneSign(response.getId());
            response.setStatusCurrent(status);
            if (SignContractStatus.APPROVED.name().equals(status)) {
                response.setCanSendForMng(true);
                response.setCanSend(false);
            }


            // man hinh sale send contract cho office-admin
            if (SignContractStatus.WAIT_APPROVE.name().equals(status)) {
                response.setCanSend(false);
                response.setCanApprove(true);
                response.setCanSign(false);
                response.setCanSendForCustomer(false);
                response.setCanUpdate(true);
                response.setCanDelete(true);
            }

            //officer-admin reject
            if (SignContractStatus.APPROVE_FAIL.name().equals(status)) {
                response.setCanSend(true);
                response.setCanApprove(false);
                response.setCanSign(false);
                response.setCanSendForCustomer(false);
                response.setRejectedBy(Objects.nonNull(obj[13]) ? obj[13].toString() : null);
                response.setCanUpdate(true);
                response.setCanDelete(true);
            }

            //send office_admin
            if (SignContractStatus.NEW.name().equals(status)) {
                response.setCanResend(false);
                response.setCanUpdate(true);
                response.setCanDelete(true);
                response.setCanApprove(true);
                response.setCanSign(false);
                response.setCanSendForCustomer(false);
            }


            if (SignContractStatus.SIGN_B_FAIL.name().equals(status)
                    || SignContractStatus.SIGN_A_FAIL.name().equals(status)) {
                response.setCanUpdate(true);
                response.setCanDelete(true);
                response.setCanSend(true);
                response.setCanSign(false);
            }

            if (SignContractStatus.SIGN_A_FAIL.name().equals(status)) {
                response.setCanSendForCustomer(false);
            }

            if (SignContractStatus.WAIT_SIGN_B.name().equals(status)
                    || SignContractStatus.WAIT_SIGN_A.name().equals(status)) {
                response.setCanUpdate(false);
                response.setCanDelete(false);
                response.setCanSend(false);
                response.setCanSendForCustomer(false);
                response.setCanSendForMng(false);
                response.setCanSign(true);
                response.setCanRejectSign(true);
            }


            List<String> statusDb = contractStatusService.checkDoneSign(response.getId());

            if (status.equals(SignContractStatus.SIGN_A_OK.name())
            ) {
                response.setCanSend(false);
                response.setCanUpdate(false);
                response.setCanDelete(false);
                if (!statusDb.contains(SignContractStatus.SUCCESS.name())) {
                    response.setCanSendForCustomer(true);
                    response.setCanSendForMng(false);
                }
            }

            if (status.equals(SignContractStatus.SIGN_B_OK.name())
            ) {
                response.setCanSend(false);
                response.setCanUpdate(false);
                response.setCanDelete(false);
                if (!statusDb.contains(SignContractStatus.SUCCESS.name())) {
                    response.setCanSendForMng(true);
                    response.setCanSendForCustomer(false);
                }
            }

            responses.add(response);
        }
        Page<ContractResponse> result = new PageImpl<>(responses, p,
                page.getTotalElements());
        return new BaseResponse(Constants.ResponseCode.SUCCESS, "", true, result);
    }

    private boolean checkLastStatusExist(String statusCheck, List<String> status) {
        for (String s : status) {
            if (s.equals(statusCheck)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getListStatusSearch(String statusSearch) {
        List<String> statusListSearch = new ArrayList<>();

        if (SignContractStatus.ALL.name().equals(statusSearch)) {
            statusListSearch.add(SignContractStatus.NEW.name());
            statusListSearch.add(SignContractStatus.APPROVED.name());
            statusListSearch.add(SignContractStatus.APPROVE_FAIL.name());
            statusListSearch.add(SignContractStatus.WAIT_APPROVE.name());
            statusListSearch.add(SignContractStatus.WAIT_SIGN_A.name());
            statusListSearch.add(SignContractStatus.WAIT_SIGN_B.name());
            statusListSearch.add(SignContractStatus.SIGN_B_FAIL.name());
            statusListSearch.add(SignContractStatus.SIGN_A_FAIL.name());
            statusListSearch.add(SignContractStatus.SIGN_A_OK.name());
            statusListSearch.add(SignContractStatus.SIGN_B_OK.name());
            statusListSearch.add(SignContractStatus.SUCCESS.name());
        }


        if (SignContractStatus.WAIT_SIGN.name().equals(statusSearch)) {
            statusListSearch.add(SignContractStatus.WAIT_SIGN_A.name());
            statusListSearch.add(SignContractStatus.WAIT_SIGN_B.name());
        }
        if (SignContractStatus.SIGN_OK.name().equals(statusSearch)) {
            statusListSearch.add(SignContractStatus.SIGN_A_OK.name());
            statusListSearch.add(SignContractStatus.SIGN_B_OK.name());
        }
        if (SignContractStatus.MANAGER_CONTRACT.name().equals(statusSearch)) {
            statusListSearch.add(SignContractStatus.NEW.name());
            statusListSearch.add(SignContractStatus.APPROVE_FAIL.name());
            statusListSearch.add(SignContractStatus.SIGN_B_FAIL.name());
            statusListSearch.add(SignContractStatus.SIGN_A_FAIL.name());
        } else {
            statusListSearch.add(statusSearch);
        }
        return statusListSearch;
    }

    @Override
    public ContractRequest findById(String id) {
        List<Object[]> lst = contractRepository.findByIdContract(id);
        ContractRequest contractRequest = new ContractRequest();
        for (Object[] obj : lst) {
            contractRequest = ContractRequest.builder()
                    .id(Objects.nonNull(obj[0]) ? obj[0].toString() : null)
                    .name(Objects.nonNull(obj[1]) ? obj[1].toString() : null)
                    .number(Objects.nonNull(obj[2]) ? obj[2].toString() : null)
                    .rule(Objects.nonNull(obj[3]) ? obj[3].toString() : null)
                    .term(Objects.nonNull(obj[4]) ? obj[4].toString() : null)
                    .partyA(PartyRequest.builder()
                            .id(Objects.nonNull(obj[5]) ? obj[5].toString() : null)
                            .name(Objects.nonNull(obj[6]) ? obj[6].toString() : null)
                            .address(Objects.nonNull(obj[7]) ? obj[7].toString() : null)
                            .taxNumber(Objects.nonNull(obj[8]) ? obj[8].toString() : null)
                            .presenter(Objects.nonNull(obj[9]) ? obj[9].toString() : null)
                            .position(Objects.nonNull(obj[10]) ? obj[10].toString() : null)
                            .businessNumber(Objects.nonNull(obj[11]) ? obj[11].toString() : null)
                            .bankId(Objects.nonNull(obj[12]) ? obj[12].toString() : null)
                            .email(Objects.nonNull(obj[13]) ? obj[13].toString() : null)
                            .bankName(Objects.nonNull(obj[14]) ? obj[14].toString() : null)
                            .bankAccOwer(Objects.nonNull(obj[15]) ? obj[15].toString() : null)
                            .build())
                    .partyB(PartyRequest.builder()
                            .id(Objects.nonNull(obj[16]) ? obj[16].toString() : null)
                            .name(Objects.nonNull(obj[17]) ? obj[17].toString() : null)
                            .address(Objects.nonNull(obj[18]) ? obj[18].toString() : null)
                            .taxNumber(Objects.nonNull(obj[19]) ? obj[19].toString() : null)
                            .presenter(Objects.nonNull(obj[20]) ? obj[20].toString() : null)
                            .position(Objects.nonNull(obj[21]) ? obj[21].toString() : null)
                            .businessNumber(Objects.nonNull(obj[22]) ? obj[22].toString() : null)
                            .bankId(Objects.nonNull(obj[23]) ? obj[23].toString() : null)
                            .email(Objects.nonNull(obj[24]) ? obj[24].toString() : null)
                            .bankName(Objects.nonNull(obj[25]) ? obj[25].toString() : null)
                            .bankAccOwer(Objects.nonNull(obj[26]) ? obj[26].toString() : null)
                            .build())
                    .file(Objects.nonNull(obj[27]) ? obj[27].toString() : null)
                    .signA(Objects.nonNull(obj[28]) ? obj[28].toString() : null)
                    .signB(Objects.nonNull(obj[29]) ? obj[29].toString() : null)
                    .createdBy(Objects.nonNull(obj[30]) ? obj[30].toString() : null)
                    .approvedBy(Objects.nonNull(obj[31]) ? obj[31].toString() : null)
                    .isUrgent(Objects.nonNull(obj[32]) ? Boolean.parseBoolean(obj[32].toString()) : null)
                    .contractTypeId(Objects.nonNull(obj[33]) ? obj[33].toString() : null)
                    .value(Objects.nonNull(obj[34]) ? (Double) obj[34] : null)
                    .status(Objects.nonNull(obj[35]) ? obj[35].toString() : null)
                    .build();
        }
        return contractRequest;
    }

    @Override
    public BaseResponse delete(String id) throws IOException {
        var contract = contractRepository.findById(id);
        if (contract.isEmpty()) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, "Contract not exist", true, null);
        }
        contract.get().setMarkDeleted(true);
        contractRepository.save(contract.get());
        elasticSearchService.deleteDocumentById("contract", id);
        return new BaseResponse(Constants.ResponseCode.SUCCESS, "", true, null);
    }

    @Override
    public BaseResponse findContractPartyById(String id) {
        Optional<Party> contractPartyOptional = partyRepository.findByTaxNumber(id);
        Party party = contractPartyOptional.orElse(null);
        return new BaseResponse(Constants.ResponseCode.SUCCESS, "", true, party);
    }

    @Override
    public Void sync() {
        List<String> ids = contractRepository.findAll().stream().map(m -> m.getId()).toList();
        ids.forEach(f -> {
            ContractRequest contract = findById(f);
            try {
                if (contract.getId() != null) {
                    contract.setSignA("");
                    contract.setSignB("");
                    elasticSearchService.indexDocument("contract", contract, ContractRequest::getId);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return null;
    }

    @Override
    public BaseResponse signContract(SignContractDTO signContractDTO) {
        ContractRequest contractRequest = findById(signContractDTO.getContractId());
        Contract contract = contractRepository.findById(signContractDTO.getContractId()).orElse(null);
        Context context = new Context();
        if (contract != null) {
            if (contract.getSignB() != null && contract.getSignA() != null) {
                return new BaseResponse(Constants.ResponseCode.SUCCESS, "Contract is successful" + contract.getContractSignDate(), true, null);
            }
            if (!signContractDTO.isCustomer()) {
                contract.setSignA(signContractDTO.getSignImage());
                context.setVariable("signA", signContractDTO.getSignImage());
                context.setVariable("signB", contract.getSignB());
                if (!StringUtils.isBlank(contract.getSignB())) {
                    contract.setStatus(Constants.STATUS.SUCCESS);
                    contractHistoryService.createContractHistory(contract.getId(), contract.getName(), contract.getCreatedBy(), signContractDTO.getComment(), Constants.STATUS.SUCCESS);
                } else {
                    contract.setStatus(Constants.STATUS.PROCESSING);
                    contractHistoryService.createContractHistory(contract.getId(), contract.getName(), contract.getCreatedBy(), signContractDTO.getComment(), Constants.STATUS.SIGN_A);
                }
            } else {
                contract.setSignB(signContractDTO.getSignImage());
                context.setVariable("signB", signContractDTO.getSignImage());
                context.setVariable("signA", contract.getSignA());
                if (!StringUtils.isBlank(contract.getSignA())) {
                    contract.setStatus(Constants.STATUS.SUCCESS);
                    contractHistoryService.createContractHistory(contract.getId(), contract.getName(), contract.getCreatedBy(), signContractDTO.getComment(), Constants.STATUS.SUCCESS);
                } else {
                    contract.setStatus(Constants.STATUS.PROCESSING);
                    contractHistoryService.createContractHistory(contract.getId(), contract.getName(), contract.getCreatedBy(), signContractDTO.getComment(), Constants.STATUS.SIGN_B);
                }
            }
            context.setVariable("partyA", contractRequest.getPartyA());
            context.setVariable("partyB", contractRequest.getPartyB());
            context.setVariable("info", contract);
            context.setVariable("date", contract.getCreatedDate());
            String html = pdfUtils.templateEngine().process("templates/new_contract.html", context);
            File file = null;
            try {
                file = pdfUtils.generatePdf(html, contract.getName() + "_" + UUID.randomUUID());
            } catch (Exception e) {
                return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
            }
            try {
                contract.setFile(cloudinaryService.uploadPdf(file));
            } catch (IOException e) {
                return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
            }
            if (file.exists() && file.isFile()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    log.warn("Failed to delete the file: {}", file.getAbsolutePath());
                }
            }
            try {
                contractRepository.save(contract);
                return new BaseResponse(Constants.ResponseCode.SUCCESS, "Sign ok", true, null);
            } catch (Exception e) {
                return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
            }
        } else {
            return new BaseResponse(Constants.ResponseCode.FAILURE, "Sign Fail", true, null);
        }
    }

    @Override
    public BaseResponse getContractSignById(String id) {
        return new BaseResponse(Constants.ResponseCode.SUCCESS, "", true, findById(id));
    }

    @Override
    public BaseResponse sendMail(String bearerToken, String[] to, String[] cc, String subject, String htmlContent, MultipartFile[] attachments, String contractId, String status, String description) {
        List<String> statusList = getListStatusSearch(SignContractStatus.ALL.name());

        if (!statusList.contains(status)) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, "status not exist", true, null);
        }
        String email = jwtService.extractUsername(bearerToken.substring(7));
        //Contract status
        List<String> receivers = new ArrayList<>();
        for (String recipient : to) {
            receivers.add(recipient.trim());
        }
        if (cc != null) {
            for (String recipient : cc) {
                receivers.add(recipient.trim());
            }
        }
        Optional<Contract> contract = contractRepository.findById(contractId);
        if (contract.isEmpty()) {
            return null;
        }
//        //màn hình hợp đồng của OFFICE_ADMIN:
//         btn phê duyệt hợp đồng : OFFICE_ADMIN approve thì sale sẽ enable btn gửi cho MANAGER (approve rồi disable)
        if (status.equals(SignContractStatus.WAIT_APPROVE.name())) {
            notificationService.create(Notification.builder()
                    .title(contract.get().getName())
                    .message("Bạn có hợp đồng mới cần kiểm tra")
                    .typeNotification("CONTRACT")
                    .receivers(receivers)
                    .sender(email)
                    .build());
        }
        if (status.equals(SignContractStatus.APPROVED.name())) {
            String approved = jwtService.extractUsername(bearerToken.substring(7));
            contract.get().setApprovedBy(approved);
            contractRepository.save(contract.get());
            notificationService.create(Notification.builder()
                    .title(contract.get().getName())
                    .message(email + " đã duyệt hợp đồng")
                    .typeNotification("CONTRACT")
                    .receivers(receivers)
                    .sender(email)
                    .build());
        }

        //officer-admin reject
        if (status.equals(SignContractStatus.APPROVE_FAIL.name())) {
            notificationService.create(Notification.builder()
                    .title(contract.get().getName())
                    .message(email + " đã yêu cầu xem lại hợp đồng")
                    .typeNotification("CONTRACT")
                    .receivers(receivers)
                    .sender(email)
                    .build());
        }

        // site a or b reject with reseon
        if (status.equals(SignContractStatus.SIGN_B_FAIL.name())
                || status.equals(SignContractStatus.SIGN_A_FAIL.name())
        ) {
            notificationService.create(Notification.builder()
                    .title(contract.get().getName())
                    .message(email + " đã từ chối kí hợp đồng")
                    .typeNotification("CONTRACT")
                    .receivers(receivers)
                    .sender(email)
                    .build());
        }
        List<String> statusDb = contractStatusService.checkDoneSign(contractId);

        if (status.equals(SignContractStatus.SIGN_A_OK.name())
        ) {
            if (SignContractStatus.SIGN_B_OK.name().equals(statusDb.get(1))) {
                contract.get().setStatus(Constants.STATUS.SUCCESS);
                contractRepository.save(contract.get());
                status = SignContractStatus.SUCCESS.name();

                notificationService.create(Notification.builder()
                        .title(contract.get().getName())
                        .message(email + " đã kí hợp đồng thành công")
                        .typeNotification("CONTRACT")
                        .receivers(receivers)
                        .sender(email)
                        .build());
            }

        }

        if (status.equals(SignContractStatus.SIGN_B_OK.name())
        ) {
            if (SignContractStatus.SIGN_A_OK.name().equals(statusDb.get(1))) {
                status = SignContractStatus.SUCCESS.name();
                contract.get().setStatus(Constants.STATUS.SUCCESS);
                contractRepository.save(contract.get());
                notificationService.create(Notification.builder()
                        .title(contract.get().getName())
                        .message(email + " đã kí hợp đồng thành công")
                        .typeNotification("CONTRACT")
                        .receivers(receivers)
                        .sender(email)
                        .build());
            }

        }


        if (status.equals(SignContractStatus.WAIT_SIGN_B.name()) || status.equals(SignContractStatus.WAIT_SIGN_A.name())) {
            notificationService.create(Notification.builder()
                    .title(contract.get().getName())
                    .message(email + " đang chờ ký")
                    .typeNotification("CONTRACT")
                    .receivers(receivers)
                    .sender(email)
                    .build());
        }
        contractStatusService.create(email, receivers, contractId, status, description);
        contractHistoryService.createContractHistory(contractId, contract.get().getName(), email, description, status);
        try {
            mailService.sendNewMail(to, cc, subject, htmlContent, attachments);
        } catch (MessagingException e) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, e.getMessage(), true, null);
        }
        return new BaseResponse(Constants.ResponseCode.SUCCESS, "ok", true, null);
    }

    @Override
    public BaseResponse getNotificationContractNumber(String email) {
        List<String> ids = contractStatusRepository.findAll().stream()
                .filter(m -> !ObjectUtils.isEmpty(m.getReceiver()) && m.getReceiver().contains(email) || !ObjectUtils.isEmpty(m.getSender()) && m.getSender().equals(email))
                .map(ContractStatus::getContractId)
                .toList();
        if(DataUtil.isNullObject(ids)) {
            return new BaseResponse(Constants.ResponseCode.SUCCESS, "Notification ", true, NotificationContractNumberDto.builder()
                    .managerCount(0)
                    .approvedCount(0)
                    .signedCount(0)
                    .waitSignCount(0)
                    .waitApprovedCount(0)
                    .successCount(0)
                    .build());
        }
        var userEmail = userRepository.findByEmail(email);

        if (userEmail.isEmpty()) {
            return new BaseResponse(Constants.ResponseCode.SUCCESS, "Email not exist ", true, null);
        }



        String[] statical = contractRepository.getNotificationContractNumber(email, ids);

        String[] parts = statical[0].split(",");
        NotificationContractNumberDto notificationContractNumberDto = NotificationContractNumberDto.builder()
                .approvedCount(Integer.parseInt(parts[0]))
                .waitApprovedCount(Integer.parseInt(parts[1]))
                .successCount(Integer.parseInt(parts[2]))
                .signedCount(Integer.parseInt(parts[3]))
                .waitSignCount(Integer.parseInt(parts[4]))
                .managerCount(Integer.parseInt(parts[5]))
                .build();

        return new BaseResponse(Constants.ResponseCode.SUCCESS, "Notification ", true, notificationContractNumberDto);
    }

    @Override
    public BaseResponse publicSendMail(String[] to, String[] cc, String subject, String htmlContent, String createdBy, String contractId, String status, String description) {
        List<String> receivers = new ArrayList<>();
        for (String recipient : to) {
            receivers.add(recipient.trim());
        }
        if (cc != null) {
            for (String recipient : cc) {
                receivers.add(recipient.trim());
            }
        }
        List<String> statusDb = contractStatusService.checkDoneSign(contractId);
        Optional<Contract> contract = contractRepository.findById(contractId);
        if (contract.isEmpty()) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, "Contract not exist", false, null);
        }

        if (status.equals(SignContractStatus.SIGN_B_OK.name())
        ) {
            if (SignContractStatus.SIGN_A_OK.name().equals(statusDb.get(1))) {
                status = SignContractStatus.SUCCESS.name();
                contract.get().setStatus(Constants.STATUS.SUCCESS);
                contractRepository.save(contract.get());
                notificationService.create(Notification.builder()
                        .title(contract.get().getName())
                        .message(createdBy + "đã kí hợp đồng thành công")
                        .typeNotification("CONTRACT")
                        .receivers(receivers)
                        .sender(createdBy)
                        .build());
            }

        }

        if (status.equals(SignContractStatus.SIGN_A_OK.name())
        ) {
            if (SignContractStatus.SIGN_B_OK.name().equals(statusDb.get(1))) {
                contract.get().setStatus(Constants.STATUS.SUCCESS);
                status = SignContractStatus.SUCCESS.name();
                contractRepository.save(contract.get());
                notificationService.create(Notification.builder()
                        .title(contract.get().getName())
                        .message(createdBy + "đã kí hợp đồng thành công")
                        .typeNotification("CONTRACT")
                        .receivers(receivers)
                        .sender(createdBy)
                        .build());
            }

        }
        try {
            mailService.sendNewMail(to, cc, subject, htmlContent, null);
        } catch (MessagingException e) {
            return new BaseResponse(Constants.ResponseCode.FAILURE, "Mail error", false, null);
        }
        contractStatusService.create(createdBy, receivers, contractId, status, description);
        return new BaseResponse(Constants.ResponseCode.SUCCESS, "ok", true, null);
    }

    @Override
    public Party createDefaultParty(Party party) {
        party.setTypeParty(true);
        return partyRepository.save(party);
    }

    @Override
    public Party getDefaultParty() {
        return partyRepository.manualParty();
    }

}
