angular.module('app').config(function($stateProvider) {
  $stateProvider
    .state('service', {
      url: '/service/{id:int}',
      resolve: {
        service: function($stateParams, ServiceService) {
          return ServiceService.get($stateParams.id);
        }
      },
      views: {
        '': {
          templateUrl: 'html/service/index.html',
          controller: 'ServiceController'
        }
      }
    })
    .state('service.general', {
      url: '/general',
      views: {
        '': {
          templateUrl: 'html/service/general.html',
          controller: 'ServiceGeneralController'
        }
      }
    })
    .state('service.instruction', {
      url: '/instruction',
      views: {
        '': {
          templateUrl: 'html/service/instruction.html',
          controller: 'ServiceInstructionController'
        }
      }
    })
    .state('service.legislation', {
      url: '/legislation',
      views: {
        '': {
          templateUrl: 'html/service/legislation.html',
          controller: 'ServiceLegislationController'
        }
      }
    })
    .state('service.questions', {
      url: '/questions',
      views: {
        '': {
          templateUrl: 'html/service/questions.html',
          controller: 'ServiceQuestionsController'
        }
      }
    })
    .state('service.discussion', {
      url: '/discussion',
      views: {
        '': {
          templateUrl: 'html/service/discussion.html',
          controller: 'ServiceDiscussionController'
        }
      }
    })
});


