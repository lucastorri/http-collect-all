angular.module('netztee-routes', [])
.value('routes', netztee.controllers);

angular.module('netztee-home', [])
.controller('HomeCtrl', ['$scope', function($scope) {
}]);

angular.module('netztee-buckets', ['netztee-routes'])
.directive('har', function() {
    return {
        restrict: 'A',
        scope: {
            har: '='
        },
        link: function($scope, elm, attrs, ctrl) {
            $scope.$watch('har', function(har) {
                elm.empty();
                if (har) {
                    elm.append('<div class="har" data-har="'+har+'" expand="true" height="400px"></div>');
                    harInitialize();
                }
            });
        }
    }
})
.controller('BucketsCtrl', ['$scope', '$http', 'routes', function($scope, $http, routes) {

    $scope.harp = routes.Har.harp;
    $scope.har = routes.Har.har;

    $http.get(routes.Har.buckets().url)
    .success(function(buckets) {
        $scope.buckets = buckets.buckets;
    });

    $scope.load = function(bucket) {
        $scope.selected = bucket;
    };

}]);

angular.module('netztee-report', [])
.value('report', { error: console.log });

angular.module('netztee', ['netztee-home', 'netztee-buckets', 'netztee-report'])
.provider('requests', function() {
    var requests = { count: 0 };
    this.$get = function() { return requests; };
})
.factory('requestsInterceptor', ['$q', '$window', 'requests', function($q, $window, requests) {
    return function(promise) {
        requests.count--;
        console.log('loaded ' + requests.count);
        return promise.then(function(response) {
            response.status == 403 && $window.location.reload();
            return response;
        }, function(response) {
            return $q.reject(response);
        });
    };
}])
/*.factory('$exceptionHandler', ['$injector', function($injector) {
    return function (e, cause) {
        var report = $injector.get('report');
        report.error(e.stack);
    };
}])*/
.config(['$locationProvider', function($locationProvider) {
    $locationProvider.html5Mode(true).hashPrefix('!');
}])
.config(['$httpProvider', 'requestsProvider', function($httpProvider, requestsProvider) {
    $httpProvider.responseInterceptors.push('requestsInterceptor');
    var requests = requestsProvider.$get();
    $httpProvider.defaults.transformRequest.push(function(data, headers) {
        requests.count++;
        console.log('loading... ' + requests.count);
        return data;
    });
}])
.config(['$routeProvider', function($routeProvider) {
    $routeProvider
    .when('/buckets', {
        templateUrl: '/template/buckets', //use routes
        controller: 'BucketsCtrl',
        pageTitle: 'Buckets',
        activeTab: '#buckets-tab'
    })
    .when('/', {
        templateUrl: '/template/home',  //use routes
        controller: 'HomeCtrl',
        pageTitle: 'Welcome',
        activeTab: '#home-tab'
    })
    .otherwise({redirectTo: '/'});
}])
.run(['$rootScope', function($rootScope) {
    $rootScope.$on('$routeChangeSuccess', function(e, current) {
        $('#nav li').removeClass('pure-menu-selected');
        if (current && current.$$route) {
            $(current.$$route.activeTab).addClass('pure-menu-selected');
            $rootScope.pageTitle = current.$$route.pageTitle;
        }
    });
}]);