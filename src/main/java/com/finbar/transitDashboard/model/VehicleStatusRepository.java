package com.finbar.transitDashboard.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.finbar.transitDashboard.model.VehicleStatus;

@Repository
public interface VehicleStatusRepository extends JpaRepository<VehicleStatus, Long> {
    // already includes methods like findAll(), save() etc.
    // can add custom queries if needed but this should be good for now I think
}
