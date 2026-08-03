package com.example.scproduct;

import com.curry.model.Product;
import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.service.ProductService;
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
        ResponseDto<Product> responseDto =  productService.
                pageQuery(null,"手机",null,null,
                        null,null,null,null,1,200, AudienceScope.unrestricted());
        List<Product> productList = responseDto.getDataList();

    }

}
