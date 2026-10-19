package com.example.scproduct;

import com.curry.model.Product;
import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.service.ProductService;
import com.example.scproduct.vo.ProductQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import response.ResponseDto;

import java.util.List;

@SpringBootTest
class ScProductApplicationTests {

    @Autowired
    private ProductService productService;

    @Test
    void contextLoads() {
        ProductQuery query = new ProductQuery();
        query.setProDesc("手机");
        query.setPageSize(200);
        ResponseDto<Product> responseDto = productService.pageQuery(query, AudienceScope.unrestricted());
        List<Product> productList = responseDto.getDataList();

    }

}
