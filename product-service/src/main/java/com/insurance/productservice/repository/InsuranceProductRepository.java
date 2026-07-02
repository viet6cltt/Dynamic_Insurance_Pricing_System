package com.insurance.productservice.repository;

import com.insurance.productservice.model.InsuranceProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, UUID> {
    Page<InsuranceProduct> findByProductTypeAndStatus(String productType, String status, Pageable pageable);
    Page<InsuranceProduct> findByProductType(String productType, Pageable pageable);
    Page<InsuranceProduct> findByStatus(String status, Pageable pageable);
}
