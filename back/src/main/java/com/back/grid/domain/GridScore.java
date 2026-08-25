package com.back.grid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "grid_score")
public class GridScore {
    @Id
    @Column(name = "grid_id")
    private Long id;

    protected GridScore() {
    }

    public Long getId() {
        return id;
    }
}
