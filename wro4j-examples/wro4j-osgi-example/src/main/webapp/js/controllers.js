'use strict';

/* Controllers */

angular.module('myApp.controllers', []).

  controller('homeCtrl', ['$scope', '$http',
    function($scope, $http) {

      var init = function () {
        $scope.files = [
          {
            name: 'Jquery',
            path: '/wro4j/wro/jquery.js'
          },
          {
            name: 'Angular',
            path: '/wro4j/wro/angular.js'
          },
          {
            name: 'Bootstrap Javascript',
            path: '/wro4j/wro/bootstrap.css'
          },
          {
            name: 'Bootstrap Styles',
            path: '/wro4j/wro/bootstrap.css'
          },
          {
            name: 'All thirdparty Javascript',
            path: '/wro4j/wro/thirdparty.js'
          },
          {
            name: 'All thirdparty Css',
            path: '/wro4j/wro/thirdparty.css'
          },
          {
            name: 'Custom Javascript',
            path: '/wro4j/wro/application.js'
          }

        ]
      };

      $scope.loadFile = function(file) {

        $http({method: 'GET', url: file.path}).
            success(function(data, status, headers, config) {

              $('#fileTextarea').val(data);

              $('#fileAnchor').attr('href', file.path);
              $('#fileAnchro').text(file.path);
              $scope.currFile = file;

            }).
            error(function(data, status, headers, config) {
              alert("Problem occurred trying to load "+file.path);
            });

      }

      init();

    }])

  .controller('statusCtrl', ['$scope', 'StatusService',
    function($scope, StatusService){
      $scope.lastError = StatusService;
    }]);