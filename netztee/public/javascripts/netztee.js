angular.module('netztee-routes', [])
.factory('routes', ['$http', function($http) {
    netztee.ajax = function(c) {
        var config = { method: c.type };
        delete c.type;
        for (var k in c) { config[k] = c[k]; }
        return $http(config);
    }
    return netztee.controllers;
}]);

angular.module('netztee-user', ['netztee-routes'])
.factory('self', ['routes', function(routes) {
    return routes.Admin.self().ajax();
}]);

angular.module('netztee-admin', [])
.directive('tableAttr', function() {
    return {
        restrict: 'A',
        require: '^tableSort',
        link: function($scope, elm, attrs, ctrl) {
            var self = attrs.tableAttr;
            var span = $('<span></span>')
            ctrl.props.push(self);
            elm.click(function() {
                ctrl.sortBy(self);
                $scope.$apply();
            });
            elm.append(span);
            function update() {
                span
                  .removeClass('table-sort-down')
                  .removeClass('table-sort-up');
                if ($scope.predicate == self) {
                    $scope.reverse ?
                        span.addClass('table-sort-down') :
                        span.addClass('table-sort-up');
                }
            }
            $scope.$watch('predicate', update);
            $scope.$watch('reverse', update);
        }
    }
})
.directive('tableExtra', ['$templateCache', function($templateCache) {
    return {
        restrict: 'A',
        require: '^tableSort',
        scope: {
            template: '@tableExtra'
        },
        link: function($scope, elm, attrs, ctrl) {
            ctrl.extra = $templateCache.get($scope.template);
        }
    }
}])
.directive('tableSort', ['$compile', function($compile) {
    return {
        restrict: 'A',
        controller: function($scope) {
            $scope.props = [];
            return $scope;
        },
        scope: {
            data: '=tableSort',
            search: '=tableFilter'
        },
        link: function($scope, elm, attrs, ctrl) {
            $scope.predicate = undefined;
            $scope.reverse = false;

            elm.find('tbody').html($compile(
                '<tr ng-repeat="d in data | filter:search | orderBy:predicate:reverse">' +
                    '<td ng-repeat="column in props">' +
                        '{{d[column]}}' +
                    '</td>' +
                    (ctrl.extra ? '<td>' + ctrl.extra + '</td>' : '') +
                '</tr>')($scope, function(srcElm, scope) {
                    scope.scope = $scope.$parent;
                }));

            $scope.a = function() {
            console.log($scope);
            }

            $scope.sortBy = function(nValue) {
                $scope.reverse = ($scope.predicate == nValue) ? !$scope.reverse : false;
                $scope.predicate = nValue;
            }

        }
    }
}])
.controller('AdminCtrl', ['$scope', 'routes', function($scope, routes) {
    routes.Admin.users().ajax()
    .success(function(users) {
        $scope.users = users.users;
    });

    $scope.activate = function(user) {
        routes.Admin.activate(user.username).ajax()
        .success(function() {
            user.active = true;
        });
    };
    $scope.deactivate = function(user) {
        routes.Admin.deactivate(user.username).ajax()
        .success(function() {
            user.active = false;
        });
    };
    $scope.status = function(user) {
        routes.Admin.status(user.username).ajax()
        .success(function() {
            user.active = true;
        })
        .error(function() {
            user.active = false;
        });
    };
}])

angular.module('netztee-home', ['netztee-user'])
.controller('HomeCtrl', ['$scope', 'self', function($scope, self) {
    self.success(function(self) {
        $scope.self = self;
    });
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
.controller('BucketsCtrl', ['$scope', 'routes', function($scope, routes) {

    $scope.harp = routes.Har.harp;
    $scope.har = routes.Har.har;

    routes.Har.buckets().ajax()
    .success(function(buckets) {
        $scope.buckets = buckets.buckets;
    });

    $scope.load = function(bucket) {
        $scope.selected = bucket;
    };

}]);

angular.module('netztee-report', [])
.value('report', { error: console.log });

angular.module('netztee', ['netztee-home', 'netztee-buckets', 'netztee-report', 'netztee-admin'])
.provider('requests', function() {
    var requests = { count: 0 };
    this.$get = function() { return requests; };
})
.factory('requestsInterceptor', ['$q', '$window', 'requests', function($q, $window, requests) {
    return function(promise) {
        requests.count--;
        requests.count == 0 && $('#loading').hide();
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
        $('#loading').show();
        return data;
    });
}])
.config(['$routeProvider', function($routeProvider) {
    $routeProvider
    .when('/admin', {
        templateUrl: '/template/admin', //use routes
        controller: 'AdminCtrl',
        pageTitle: 'Admin',
        activeTab: '#admin-tab'
    })
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