package org.choongang.global.config.containers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.choongang.global.config.annotations.*;
import org.choongang.global.config.containers.mybatis.MapperProvider;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeanContainer {
    private static BeanContainer instance;

    private Map<String, Object> beans; // bean 형태로 들어가있다. 얘가 가지고 있는 관리 객체이다. 관리 객체를 Map 형태로 넘긴다.
    // 보통은 T 값을 class 명을 가지고 등록을 한다. 패키지를 포함한 전체 클래스 명 가지고 T를 구성하고 두번째는 Object면 모든 객체를 담을 수 있는 형태라는 것이다.
    // T 값은 전체 클래스 명이고, Object는 모든 객체를 담을 수 있는 객체 컨테이너이다. 담을 수 있는 공간이라는 것이다.ㅣ

    private MapperProvider mapperProvider; // 마이바티스 매퍼 조회
    // 별도로 만들었다. mybatis 매퍼 객체는 싱글톤으로 관리해야한다. DB가 갱신이 안 되기 때문에 아무리 insert, update 해도 반영이 안 된다.
    // 그래서 그걸 방지하기 위해서 얘는 별도로 매번 생성할 수 있게 넣어놨다. 이걸 받을 수 있는 객체는 Provider이다.
    // 거의 대부분은 싱글톤으로 관리하지만 싱글톤으로 관리하지 않는 객체도 몇 개 있다. 서블릿 기본 객체는 요청시마다 달라져야하니까 HttpServletRequest, HttpServletResponse, HttpSession 매번 갱신,
    // mapper도 그 중 하나이다. mapper도 매번 요청이 들어 올 때마다 새로운 객체로 갱신한다. 이것도 싱글톤으로 하면 insert, update가 반영이 안 된다.

    public BeanContainer() {
        beans = new HashMap<>();
        mapperProvider = MapperProvider.getInstance();
    }

    public void loadBeans() { // loadBean() -> 얘가 전체를 가져와서 특정 애노테이션에 붙어있는 클래스에 객체를 자동 생성해주고 해당하는 의존성이 있으면 의존성을 해결해준다.
        // 패키지 경로 기준으로 스캔 파일 경로 조회
        try {
            String rootPath = new File(getClass().getResource("../../../").getPath()).getCanonicalPath();  // getResource("../../../").getPath() -> 현재 클래스 기준에서 컴파일 된 클래스 파일(BeanContainer()) 기준의 경로가 이거다.
            // 이걸 쓰게 되면 이건 어떤 경로냐면
            // getClass().getResource(…) : 현재 클래스(BeanContainer) 파일의 물리적 경로가 나오게 된다. 이걸 쓰는 이유는 컴파일 되면 이 빌드 경로가 들어간다 자바 파일은 배제돼서 안되고 컴파일 됐을 때 경로 기준에서 찾기 위해서 이걸 넣었다.
            //                 build/…/BeanContainer.class  -> 얘 경로 기준에서 찾아본다. 자바 파일로 하면 절대 안 된다. class 파일로 해야 나중에 배포 했을 때도 문제가 없다.
            // 현재 ("../../../")이다. 상대 경로 형태로 올라갔다. 현재 얘를 내가 접속하게 되면 경로가 org/choongang/global/config/containers에 Beancontainer.java가 있다.
            // 여기서 내가 검색하는 경로는 Beancontainer.java 파일이 있는 위치에서 3번 올라가면 org/choongang쪽으로 맞춰지게 된다. 여기서 부터 시작해서 모든 파일을 재귀적으로 다 가져와서 애노테이션을 다 체크해본다. 여기가 바로 스캔할 시작점이다.
            // getResource가 현재 클래스 기준에서 절대 경로가 나오게 된다. "../../../"으로 org/choongang이 맞춰지게 되고 정확하게 하기 위해서 CanonicalPath() -> 정규화된 Path를 만들어주었다. 원래 실제 경로로 바뀌어지게된다.

            String packageName = getClass().getPackageName().replace(".global.config.containers", "");
            // packageName 쓸 곳이 있어서 만들었다. 가져오게 되면 기준 패키지 이름은 org.choongang이 된다. 하지만 패키지 명도 사실 바뀔 수 있다. 상대적으로 접근한다고 보면 된다.
            // ".global.config.containers"를 지우게 되면 패키지명은 org.choongang만 남게 된다. -> 제일 첫번째 줄에 package org.choongang;만 남게 된다는 것이다. 이게 기준이 될 수 있는 패키지 명이다.
            List<Class> classNames = getClassNames(rootPath, packageName);
            // 경로와 기준 패키지를 try 아래 두 구문으로 만들었다. 그걸 통해서 현재 모든 경로에 있는 모든 클래스의 이름(즉, 패키지를 포함한 모든 클래스명을이 경로만 알고 있으면 다 가져올 수 있다.

            for (Class clazz : classNames) {
                // 인터페이스는 동적 객체 생성을 하지 않으므로 건너띄기
                if (clazz.isInterface()) {
                    continue;
                }

                // 애노테이션 중 Controller, RestController, Component, Service, ControllerAdvice, RestControllerAdvice 등이 TYPE 애노테이션으로 정의된 경우 beans 컨테이너에 객체 생성하여 보관
                // 키값은 전체 클래스명, 값은 생성된 객체
                String key = clazz.getName();

                /**
                 *  이미 생성된 객체라면 생성된 객체로 활용
                 *  매 요청시마다 새로 만들어야 객체가 있는 경우 갱신 처리
                 *
                 *  매 요청시 새로 갱신해야 하는 객체
                 *      - HttpServletRequest
                 *      - HttpServletResponse
                 *      - HttpSession session
                 *      - Mybatis mapper 구현 객체
                 */

                if (beans.containsKey(key)) {
                    updateObject(beans.get(key));
                    continue;
                }


                Annotation[] annotations = clazz.getDeclaredAnnotations();

                boolean isBean = false;
                for (Annotation anno : annotations) {
                    if (anno instanceof Controller || anno instanceof RestController || anno instanceof Service || anno instanceof Component || anno instanceof ControllerAdvice || anno instanceof RestControllerAdvice)  {
                        isBean = true;
                        break;
                    }
                }
                // 컨테이너가 관리할 객체라면 생성자 매개변수의 의존성을 체크하고 의존성이 있다면 해당 객체를 생성하고 의존성을 해결한다.
                if (isBean) {
                    Constructor con = clazz.getDeclaredConstructors()[0];
                    List<Object> objs = resolveDependencies(key, con);
                    if (!beans.containsKey(key)) {
                        Object obj = con.getParameterTypes().length == 0 ? con.newInstance() : con.newInstance(objs.toArray());
                        beans.put(key, obj);
                    }
                }

            }



        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BeanContainer getInstance() {
        if (instance == null) {
            instance = new BeanContainer();
        }

        return instance;
    }

    /**
     * 생성된 객체 조회
     *
     * @param clazz
     * @return
     */
    public <T> T getBean(Class clazz) {
        return (T)beans.get(clazz.getName());
    }

    public void addBean(Object obj) {

        beans.put(obj.getClass().getName(), obj);
    }

    public void addBean(String key, Object obj) {
        beans.put(key, obj);
    }

    // 전체 컨테이너 객체 반환
    public Map<String, Object> getBeans() {
        return beans;
    }

    /**
     * 의존성의 의존성을 재귀적으로 체크하여 필요한 의존성의 객체를 모두 생성한다.
     *
     * @param con
     */
    private List<Object> resolveDependencies(String key, Constructor con) throws Exception {
        List<Object> dependencies = new ArrayList<>();
        if (beans.containsKey(key)) {
            dependencies.add(beans.get(key));
            return dependencies;
        }

        Class[] parameters = con.getParameterTypes();
        if (parameters.length == 0) {
            Object obj = con.newInstance();
            dependencies.add(obj);
        } else {
            for(Class clazz : parameters) {
                /**
                 * 인터페이스라면 마이바티스 매퍼일수 있으므로 매퍼로 조회가 되는지 체크합니다.
                 * 매퍼로 생성이 된다면 의존성 주입이 될 수 있도록 dependencies에 추가
                 *
                  */
                if (clazz.isInterface()) {
                    Object mapper = mapperProvider.getMapper(clazz);
                    if (mapper != null) {
                        dependencies.add(mapper);
                        continue;
                    }
                }

                Object obj = beans.get(clazz.getName());
                if (obj == null) {
                    Constructor _con = clazz.getDeclaredConstructors()[0];

                    if (_con.getParameterTypes().length == 0) {
                        obj = _con.newInstance();
                    } else {
                        List<Object> deps = resolveDependencies(clazz.getName(), _con);
                        obj = _con.newInstance(deps.toArray());
                    }
                }
                dependencies.add(obj);
            }
        }


        return dependencies;
    }

    private List<Class> getClassNames(String rootPath, String packageName) {
        List<Class> classes = new ArrayList<>();
        List<File> files = getFiles(rootPath);
        for (File file : files) {
            String path = file.getAbsolutePath();
            String className = packageName + "." + path.replace(rootPath + File.separator, "").replace(".class", "").replace(File.separator, ".");
            try {
                Class cls = Class.forName(className);
                classes.add(cls);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return classes;
    }

    private List<File> getFiles(String rootPath) {
        List<File> items = new ArrayList<>();
        File[] files = new File(rootPath).listFiles();
        if (files == null) return items;

        for (File file : files) {
            if (file.isDirectory()) {
                List<File> _files = getFiles(file.getAbsolutePath());
                if (!_files.isEmpty()) items.addAll(_files);
            } else {
                items.add(file);
            }
        }
        return items;
    }

    /**
     * 컨테이너에 이미 담겨 있는 객체에서 매 요청시마다 새로 생성이 필요한 의존성이 있는 경우
     * 갱신 처리
     *  - HttpServletRequest
     *  - HttpServletResponse
     *  - HttpSession session
     *  - Mybatis mapper 구현 객체
     *
     * @param bean
     */
    private void updateObject(Object bean) {
        // 인터페이스인 경우 갱신 배제
        if (bean.getClass().isInterface()) {
            return;
        }

        Class clazz = bean.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            Class clz = field.getType();
            try {

                /**
                 * 필드가 마이바티스 매퍼 또는 서블릿 기본 객체(HttpServletRequest, HttpServletResponse, HttpSession) 이라면 갱신
                 *
                 */
                
                Object mapper = mapperProvider.getMapper(clz);

                // 그외 서블릿 기본 객체(HttpServletRequest, HttpServletResponse, HttpSession)이라면 갱신
                if (clz == HttpServletRequest.class || clz == HttpServletResponse.class || clz == HttpSession.class || mapper != null) {
                    field.setAccessible(true);
                }

                if (clz == HttpServletRequest.class) {
                    field.set(bean, getBean(HttpServletRequest.class));
                } else if (clz == HttpServletResponse.class) {
                    field.set(bean, getBean(HttpServletResponse.class));
                } else if (clz == HttpSession.class) {
                    field.set(bean, getBean(HttpSession.class));
                } else if (mapper != null) { // 마이바티스 매퍼
                    field.set(bean, mapper);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
