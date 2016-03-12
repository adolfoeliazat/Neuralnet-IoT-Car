package com.zuehlke.carrera.javapilot.rest;

import com.zuehlke.carrera.javapilot.services.PilotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
    public class RestApiController {

    @Autowired
    public PilotService service;

    @RequestMapping(value="/replay/{tag}", method = RequestMethod.GET,  produces = "application/json")
    public String replay (@PathVariable String tag )  {

        service.replay ( tag );
        return "ok";
    }


}
