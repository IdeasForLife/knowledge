package com.mark.knowledge.rag.app;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web 页面控制器 - 提供 RAG 演示界面
 *
 * @author mark
 */
@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
