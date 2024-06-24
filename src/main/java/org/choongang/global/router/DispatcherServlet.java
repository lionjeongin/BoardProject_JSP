package org.choongang.global.router;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.choongang.global.config.annotations.*;
import org.choongang.global.config.containers.BeanContainer;

import java.io.IOException;

@WebServlet("/") // 모든 요청이 전부다 여기에 유입될 수 있게 하기 위해서 주소가 "/" 얘 하나만 등록되어있다. 이 안에서 라우팅을 한다.
public class DispatcherServlet extends HttpServlet  {

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        //  요청 메서드 중에서 service! 바로 위 service 메서드는 모든 요청이 다 유입된다. 모든 요청이 오게 되면 service가 컨트롤러를 찾고 실행하게 된다. 모든 주소와 모든 메서드 상관 없이 다 유입되게 해준다. 그게 바로 service!!
        BeanContainer bc = BeanContainer.getInstance(); // 객체 컨테이너 만들었다. 객체 컨테이너는 객체를 관리하는 것이고 관리할 객체를 불러오고 객체를 만들고 의존성을 해결해야한다.
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        bc.addBean(HttpServletRequest.class.getName(), request);
        bc.addBean(HttpServletResponse.class.getName(), response);
        bc.addBean(HttpSession.class.getName(), request.getSession());
        // 위 세줄은 수기로 내가 등록했다. Servlet 기본 객체인 HttpServletRequest, HttpServletResponse, HttpSession 이 세가지를 수동으로 설정하고 있다.
        // 이유가 기본 빈 객체 컨테이너 관리방식은 싱글톤이다. 한 번 이미 객체가 되어 있으면 더이상 만들지 않는다. 근데 이 세가지 객체는 요청시마다 매번 바뀔수 있는 객체이다.
        // 그래서 이 세 객체는 매번 새로 하나 만들어서 객체가 새로 불러와서 매번 다시 수동으로 set한다. 그 외엔 기본적으로 싱글톤으로 관리한다.

        bc.loadBeans(); // 모든 객체를 불러온다. 그치만 불러오는 것에도 기준이 있는데 특정 애노테이션이 있으면 그 애노테이션의 정보가 있는 객체만 전부다 생성해주고 의존성(그 객체가 필요로 하는 의존성)을 전부다 주입해준다.
        // 의존성은 어떻게 정의하냐면 생성자 매개변수에 정의하는 걸 전부다 의존성이라고 한다. 이 객체 컨트롤 관련된 객체이면 알아서 생성해서 넣어준다.
        // 따로 객체를 조립하거나 할 필요없이 bc.loadBeans()가 얘가 객체 조립을 담당한다. 따라서 객체 조립기를 bc.loadBeans(); <- 이 Bean컨테이너가 담당해준다.
        // 생성자 매개변수 왜 나왔냐고? 누가 조립을 해야되는데 내가 하지 않고 얘가 대신해준다.

        /*
        BeanContainer::loadBeans() : 모든 관리 객체 로드(객체 생성 및 의존성 주입)
        → 요청이 들어오면 실행된다. 객체를 관리해주는 컨테이너다. 객체관리 기본은 싱글톤 형태로 관리하고 객체 안에 객체가 필요로 하는 객체가 있다면 찾아서 생성해주고 주입해준다.
        new하고 따로 객체를 만들 필요가 없이 명시만 하거나 알려만 줘도 알아서 객체가 생성된다. 의존성도 다 주입이 된다.

        -> 빈 컨테이너가 어떻게 주입하지?
        - 특정 애노테이션이 있는 객체를 자동 생성, 의존하는 객체 주입한다.

        내가 정의한 특정 애노테이션
        - @Controller : 컨트롤러 역할 객체
        - @RestController : 컨트롤러 역할 객체 - 응답시 JSON 형태로 응답
        - @Service : 기능에 대한 내용을 정의할 때 쓴다. 이름도 명확하게 서비스 쪽 기능!
        - @Component : 기능에 대한 내용을 정의할 때 쓴다.
        - @ControllerAdvice : @Controller의 공통 처리
        - @RestControllerAdvice : @RestController 의 공통 처리
        → 애노테이션은 알려주는 것일 뿐이다. 그냥 정보 전달이다. 절대 애노테이션은 기능이 아니다. 정보이다. 정보 조회를 할 수 있게 한다!! 이런게 붙어있으면 관련 객체임을 알려준다. 빈 컨테이너가 객체임을 알려준다.
         */

        RouterService service = bc.getBean(RouterService.class);
        service.route(request, response);
        // 지금 들어와있는 요청을 분석해보고, 요청에 맞는 컨트롤러를 찾아주는 라우팅을 해야한다. 바로 RouterService가 하나 만들어지고 마찬가지로 service라는 애노테이션이 들어가있다. 얘도 마찬가지로 빈 컨테이너와 관련된 객체다.
        // getBean하고 class명으로 조회하면 이미 생성되어 있는 객체 컨테이너 안에 있는 객체를 불러와서 사용하게 된다. 얘는 싱글톤 형태로 관리한다.
        // 요청과 응답에 대한 객체를 넣어주고 route를 하게 되면 이제 컨트롤 매핑이 찾는 역할을 하게 된다.
    }
}
