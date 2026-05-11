package com.danzzan.domain.boothmap.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "pub_display_day",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_pub_display_day", columnNames = {"pub_id", "pub_operation_id"})
        }
)
public class PubDisplayDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pub_id", nullable = false)
    private Pub pub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pub_operation_id", nullable = false)
    private PubOperation pubOperation;

    public PubDisplayDay(Pub pub, PubOperation pubOperation) {
        this.pub = pub;
        this.pubOperation = pubOperation;
    }
}
