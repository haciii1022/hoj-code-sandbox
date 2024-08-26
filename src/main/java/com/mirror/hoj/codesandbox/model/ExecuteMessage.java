package com.mirror.hoj.codesandbox.model;

import lombok.Data;

/**
 * @author Mirror
 * @date 2024/8/13
 */
@Data
public class ExecuteMessage {

    private Integer exitValue;

    private String message;

    private String errorMessage;

    private Long time;
}
