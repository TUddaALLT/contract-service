package com.fpt.servicecontract.contract.repository;

import com.fpt.servicecontract.contract.model.PaySlip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.Optional;


@Repository
public interface PaySlipRepository extends JpaRepository<PaySlip, String> {
    @Query(value = """
                    SELECT pl.id, pl.email, commission_percentage, total_value_contract,
                           base_salary, client_deployment_percentage, bonus_reaches_threshold, food_allowance,
                           transportation_or_phone_allowance, total_salary, pl.created_date,
                           u.name, u.phone, u.department, u.position, u.address
                    FROM
                        pay_slip pl
                    join
                        users u on pl.email = u.email
                    WHERE
                        (month(pl.created_date) = :monthSearch or :monthSearch is null)
                        and (year(pl.created_date) = :yearSearch or :yearSearch is null)
                        and (pl.type = :type or :type is null)
                        and (pl.email = :email or :email is null)
                        order by pl.created_date desc
            """, nativeQuery = true)
    Page<Object[]> getAllPaySlip(Pageable pageable, Integer monthSearch, Integer yearSearch, String type, String email);

    @Query(value = """
                select count(pl.id), sum(pl.total_value_contract)
                from pay_slip pl
                WHERE
                        (year(pl.created_date) = :yearSearch or :yearSearch is null)
                        AND pl.type = :type
            """, nativeQuery = true)
    Optional<Object[]> getTotalValueContractOneYear(Integer yearSearch, String type);

}
