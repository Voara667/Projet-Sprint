package testapp.controller;

import framework.core.ModelAndView;
import framework.core.annotation.Controller;
import framework.core.annotation.RequestMapping;
import framework.core.HttpMethod;
import testapp.model.Employe;
import java.util.ArrayList;
import java.util.List;

@Controller
public class EmpController {

    @RequestMapping(value = "/emp/list", method = HttpMethod.GET)
    public ModelAndView list() {
        ModelAndView mv = new ModelAndView();
        mv.setUrl("emp/list");
        List<Employe> emps = new ArrayList<>();
        emps.add(new Employe("Dupont", "Jean"));
        emps.add(new Employe("Martin", "Claire"));
        mv.setAttribute("employees", emps);
        mv.setAttribute("message", "Liste des employés");
        return mv;
    }
}
