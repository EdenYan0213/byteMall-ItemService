package com.yccc.bytemall.entity.dto;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ItemDTO implements Serializable {
    private Long id;
    private String name;
    private Integer price;
    private String image;
    private List<String> category;
    private String brand;
    private Integer stock;
    private String description;
}
