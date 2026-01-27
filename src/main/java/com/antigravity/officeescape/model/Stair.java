package com.antigravity.officeescape.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Stair {
    private double x;
    private double y;
    private double width;
    private StairType type;
}
